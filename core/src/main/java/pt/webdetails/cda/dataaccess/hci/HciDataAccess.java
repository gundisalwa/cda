package pt.webdetails.cda.dataaccess.hci;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.TableModel;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.pentaho.reporting.engine.classic.core.ParameterDataRow;
import org.pentaho.reporting.engine.classic.core.cache.CachingDataFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.webdetails.cda.connections.ConnectionCatalog.ConnectionType;
import pt.webdetails.cda.connections.hci.HciConnection;
import pt.webdetails.cda.connections.hci.HciFacetRequests;
import pt.webdetails.cda.connections.hci.HciSearchRequest;
import pt.webdetails.cda.connections.hci.HciSearchResultsModel;
import pt.webdetails.cda.dataaccess.QueryException;
import pt.webdetails.cda.dataaccess.SimpleDataAccess;
import pt.webdetails.cda.utils.HttpUtil;
import pt.webdetails.cda.utils.HttpUtil.Response;

public class HciDataAccess extends SimpleDataAccess {
	private static int offset;
	private static String lastIndex;
	private static String lastQuery;
	private static final int ITEMSTORETURN = 10;
	
	public HciDataAccess() {}
	
	public HciDataAccess(final Element element ) {
		super( element );
	}

	@Override
	protected IDataSourceQuery performRawQuery(ParameterDataRow parameterDataRow)
			throws QueryException {
		String url = null;
		HciDataSourceQuery query = null;
		HciConnection connection;
		try {
			connection = (HciConnection) getCdaSettings().getConnection( getConnectionId() );
			url = connection.getConnectionInfo().getUrl();
			String jsonRequest = buildRequest();
			Map<String, String> headerMap = new HashMap<String, String>();
			headerMap.put("Content-Type", "application/json");
			Response response = HttpUtil.doPost(url, jsonRequest, headerMap);
			if (response.getStatusCode() == 200) {
				String body = response.getBody();
				HciSearchResultsModel searchResults = (HciSearchResultsModel) deserializeFromJson(body, HciSearchResultsModel.class);
				HciTableModel model = new HciTableModel(searchResults.getResults());			
				query = new HciDataSourceQuery (model, null);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return query;
	}

	private String buildRequest() {
		HciSearchRequest searchRequest = new HciSearchRequest();	
		SAXReader reader = new SAXReader();
		Document doc;
		try {
			doc = (Document) reader.read(new StringReader(getQuery()));
			parseXMLQuery(searchRequest, doc.getRootElement());
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		String request = serializeToJson(searchRequest);
		return request;
	}

	@SuppressWarnings("unchecked")
	private void parseXMLQuery(HciSearchRequest searchRequest, Element ele) {
		String indexName = (String) ele.selectObject( "string(./schemaName)" );
		String queryString = (String) ele.selectObject( "string(./query)" );
		searchRequest.setQueryString(queryString);
		searchRequest.setIndexName(indexName);
		ArrayList<HciFacetRequests> facetRequests = new ArrayList<HciFacetRequests>();
		List<Node> nodes = ele.selectNodes("./facetRequests/facet");
		
		for (Node node : nodes) {
			HciFacetRequests facets = new HciFacetRequests();
			facets.setFieldName(node.valueOf("@field"));
			facets.setMaxCount(Integer.parseInt(node.valueOf("@maxCount")));
			facets.setMinCount(Integer.parseInt(node.valueOf("@minCount")));
			List<Node> subNodes = node.selectNodes("termFilter");
			ArrayList<String> termFilters = new ArrayList<String>();
			for (Node subNode : subNodes) {
				termFilters.add(subNode.getText());
			}
			facets.setTermFilters(termFilters);
			facetRequests.add(facets);
		}
		if (indexName.equals(lastIndex) && queryString.equals(lastQuery)) {
			offset += ITEMSTORETURN;
		} else {
			offset = 0;
		}
		searchRequest.setOffset(offset);
		searchRequest.setItemsToReturn(ITEMSTORETURN);
		searchRequest.setFacetRequests(facetRequests);
		lastIndex = indexName;
		lastQuery = queryString;
	}

	private String serializeToJson(Object obj) {
		Gson gson = null;
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		gson = builder.create();
		return gson.toJson(obj);
	}
	
	public static Object deserializeFromJson(String json, Class<?> classObject) {
		Gson gson = new Gson();
		return gson.fromJson(json, classObject);
	}

	@Override
	public String getType() {
		return "hci";
	}

	@Override
	public ConnectionType getConnectionType() {
		return ConnectionType.HCI;
	}
	
	protected static class HciDataSourceQuery implements IDataSourceQuery {

	    private TableModel tableModel;
	    private CachingDataFactory localDataFactory;

	    public HciDataSourceQuery( TableModel tm, CachingDataFactory df ) {
	      this.tableModel = tm;
	      this.localDataFactory = df;
	    }

	    @Override
	    public TableModel getTableModel() {
	      return tableModel;
	    }

	    @Override
	    public void closeDataSource() throws QueryException {}
	}

}
