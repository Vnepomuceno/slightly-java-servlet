package biz.netcentric.servlet;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Java servlet that processes requests for HTML templates, which are expected to
 * contain expressions that can be later evaluated and rendered as valid HTML.
 *
 * @author Valter Nepomuceno
 * @version 1.0
 * @since 15th June 2017
 */
public class SlightlyProcessor extends HttpServlet {

	/** Servlet default path */
	private static final String DEFAULT_PATH = "/";

	/** Servlet path to document index.html */
	private static final String INDEX_PATH = "/index.html";

	/** HTML document charset */
	private static final String CHARSET_NAME = "UTF-8";

	/** Servlet response content type */
	private static final String RESPONSE_CONTENT_TYPE = "text/html;charset=UTF-8";

	/** Attribute name for tag script containing Javascript code */
	private static final String JS_ATTRIBUTE_NAME = "type";

	/** Attribute value for tag script containing Javascript code */
	private static final String JS_ATTRIBUTE_VALUE = "server/javascript";

	/** Attribute name for 'data-if' expressions */
	private static final String DATA_IF_ATTRIBUTE_NAME = "data-if";

	/** Prefix for 'data-for-x' expressions */
	private static final String DATA_FOR_ATTRIBUTE_PREFIX = "data-for";

	/** Index of 'x' variable in 'data-for-x' expressions */
	private static final int DATA_FOR_X_IDX = 2;

	/** Prefix for $-expressions */
	private static final String EXPRESSION_PREFIX = "${";

	/** Suffix for $-expressions */
	private static final String EXPRESSION_SUFFIX = "}";

	/** String builder for servlet response */
	private StringBuilder responseBuilder;

	/** Nashorn Javascript engine */
	private ScriptEngine nashorn;

	/**
	 * Initializes Slightly servlet and instantiates Nashorn engine.
	 */
	public SlightlyProcessor() {
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		nashorn = scriptEngineManager.getEngineByName("nashorn");
		evaluateJavascript("load('nashorn:mozilla_compat.js')", nashorn);
	}

	/**
	 * Executed when servlet receives a POST request.
	 * @param request Servlet request.
	 * @param response Servlet response.
	 * @throws ServletException Exception thrown during request processing.
	 * @throws IOException Exception thrown during document loading.
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		prepareResponse(request, response);
	}

	/**
	 * Executed when servlet receives a GET request.
	 * @param request Servlet request.
	 * @param response Servlet response.
	 * @throws ServletException Exception thrown during request processing.
	 * @throws IOException Exception thrown during document loading.
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		prepareResponse(request, response);
	}

	/**
	 * Prepares servlet response to return by doing the following:
	 * 1. Loads HTML document.
	 * 2. Evaluates Javascript code present in the document.
	 * 3. Evaluates 'data-if' expressions.
	 * 4. Evaluates 'data-for-x' expressions.
	 * 5. Evaluates $-expressions.
	 * 6. Prints evaluated and processed response.
	 *
	 * In case an exception is thrown, the exception message is returned in the response.
	 *
	 * @param request Servlet request.
	 * @param response Servlet response.
	 * @throws ServletException Exception thrown during request processing.
	 * @throws IOException Exception thrown during document loading.
	 */
	private void prepareResponse(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		responseBuilder = new StringBuilder();
		String urlPath = request.getPathInfo().equals(DEFAULT_PATH) ? INDEX_PATH : request.getPathInfo();

		try {
			// Loads servlet context and Jsoup parses HTML file into Document object
			ServletContext servletContext = getServletConfig().getServletContext();
			File htmlFile = new File(servletContext.getResource(urlPath).toURI());
			Document htmlDocument = Jsoup.parse(htmlFile, CHARSET_NAME);

			// Loads Javascript code
			String javascriptCode = htmlDocument.getElementsByAttributeValue(JS_ATTRIBUTE_NAME, JS_ATTRIBUTE_VALUE).html();

			// Populates Nashorn engine with request
			nashorn.put("request", request);

			// Evaluates javascript code
			evaluateJavascript(javascriptCode, nashorn);

			// Evaluates 'data-if' expressions
			evaluateDataIfExpressions(htmlDocument, nashorn);

			// Evaluates 'data-for-x' expressions
			evaluateDataForExpressions(htmlDocument, nashorn);

			// Evaluates $-expressions
			responseBuilder.append(evaluateExpressions(htmlDocument, nashorn));

		} catch (FileNotFoundException fnfe) {
			prepareOutput(responseBuilder, "(Page Not Found) " + fnfe.getMessage());
		} catch (ScriptException se) {
			prepareOutput(responseBuilder, "(Javascript Error) " + se.getMessage());
		} catch (Exception e) {
			prepareOutput(responseBuilder, "(General Error) " + e.getMessage());
		} finally {
			printResponse(response, responseBuilder.toString());
		}
	}

	/**
	 * Evaluates 'data-if' expressions by doing the following:
	 * 1. Finds all 'data-if' expressions and evaluates its content.
	 * 2. If the evaluation result is true, the element is printed, otherwise the element is removed.
	 *
	 * @param htmlDocument HTML document requested.
	 * @param engine Nashorn Javascript engine.
	 */
	private void evaluateDataIfExpressions(Document htmlDocument, ScriptEngine engine) {
		Elements dataIfElements = htmlDocument.getElementsByAttributeStarting(DATA_IF_ATTRIBUTE_NAME);
		dataIfElements.forEach((element) -> {
			Object result = evaluateJavascript(element.attr(DATA_IF_ATTRIBUTE_NAME), engine);
			if (result != null && !Boolean.valueOf(result.toString())) element.remove();

			element.removeAttr(DATA_IF_ATTRIBUTE_NAME);
		});
	}

	/**
	 * Evaluates 'data-for-x' expressions by doing the following:
	 * 1. Finds all 'data-for-x' expressions.
	 * 2. Identifies 'x' value.
	 * 3. Evaluates Javascript expressions corresponding to the elements to display.
	 * 4. Generates elements in a list replacing 'x' value with the value from evaluation.
	 *
	 * @param htmlDocument HTML document requested.
	 * @param engine Nashorn Javascript engine.
	 */
	private void evaluateDataForExpressions(Document htmlDocument, ScriptEngine engine) {
		Elements dataElements = htmlDocument.getElementsByAttributeStarting(DATA_FOR_ATTRIBUTE_PREFIX);
		dataElements.forEach(element -> {
			Optional<Attribute> forAttribute = element.attributes().asList().stream().filter(
					attribute -> attribute.getKey().startsWith(DATA_FOR_ATTRIBUTE_PREFIX)).findFirst();

			if (forAttribute.isPresent()) {
				String fullForAttribute = forAttribute.get().getKey();
				String forAttributeWildCard = fullForAttribute.split("-")[DATA_FOR_X_IDX];

				Object result = evaluateJavascript(element.attr(fullForAttribute), engine);
				ArrayList<String> childValues = (ArrayList<String>) result;
				element.removeAttr(fullForAttribute);

				if (childValues != null) {
					childValues.forEach(forElement -> {
						String elHtml = element.outerHtml();
						elHtml = elHtml.replace(EXPRESSION_PREFIX
								+ forAttributeWildCard + EXPRESSION_SUFFIX, forElement);
						element.before(elHtml);
					});
				}
				element.remove();
			}
		});
	}

	/**
	 * Evaluates $-expressions by doing the following:
	 * 1. Finds all $-expressions.
	 * 2. Evaluates expressions and replaces their value.
	 *
	 * @param htmlDocument HTML document requested.
	 * @param engine Nashorn Javascript engine.
	 * @return Servlet response.
	 * @throws ScriptException Exception thrown during Javascript evaluation.
	 */
	private String evaluateExpressions(Document htmlDocument, ScriptEngine engine) throws ScriptException {
		String html = htmlDocument.html();
		String[] expressions = StringUtils.split(html, EXPRESSION_PREFIX);
		for (int i = 1; i < expressions.length; i++)
		{
			String expression = expressions[i].substring(0, expressions[i].indexOf(EXPRESSION_SUFFIX));
			Object result = evaluateJavascript(expression, engine);
			if (result != null) {
				expressions[i] = expressions[i].replace(expression + EXPRESSION_SUFFIX, result.toString());
			}
		}

		return StringUtils.join(expressions);
	}

	/**
	 * Using Nashorn Javascript engine, evaluates the Javascript code given as input and returns
	 * an Object as result of the Javascript code evaluation.
	 * @param javascriptCode Javascript code to evaluate.
	 * @param nashorn Nashorn Javascript engine.
	 * @return Object resultant of evaluating Javascript code.
	 */
	private Object evaluateJavascript(String javascriptCode, ScriptEngine nashorn) {
		try {
			return nashorn.eval(javascriptCode);
		} catch (ScriptException e) {
			prepareOutput(responseBuilder, e.getMessage());
		}

		return null;
	}

	/**
	 * Prints out the servlet response.
	 * @param response Servlet response.
	 * @param text Text to be printed in the response.
	 * @throws IOException Exception thrown during document loading.
	 */
	private void printResponse(HttpServletResponse response, String text)
			throws IOException {
		response.setContentType(RESPONSE_CONTENT_TYPE);

		try (PrintWriter out = response.getWriter()) {
			out.println(text);
		}
	}

	/**
	 * Prints message into servlet response.
	 * @param response Servlet response.
	 * @param message Message to print into response.
	 */
	private void prepareOutput(StringBuilder response, String message) {
		response.delete(0, response.length());
		response.append(message);
	}
}
