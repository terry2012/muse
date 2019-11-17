package edu.wm.cs.muse.dataleak;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.wm.cs.muse.dataleak.support.Arguments;
import edu.wm.cs.muse.dataleak.support.FileUtility;
import edu.wm.cs.muse.dataleak.support.OperatorType;

/**
 * @author liz
 *
 *         This class contains all the information pertaining to the data leak
 *         security operator. More specifically, it contains the sink and source
 *         for each sink, source, and reachability operator schema.
 * 
 *         Sample data leak formats: Declaration: String dataLeak{{ IDENTIFIER
 *         }}; Source: dataLeak{{ IDENTIFIER }} =
 *         java.util.Calendar.getInstance().getTimeZone().getDisplayName();
 *         Sink: android.util.Log.d("leak-{{ IDENTIFIER }}", dataLeak{{
 *         IDENTIFIER }}); Hop: dataLeak{{ IDENTIFIER }} = dataLeak{{ IDENTIFIER
 *         }};
 * 
 */
public class DataLeak {

	// default source and sink strings
	private static HashMap<OperatorType, String> sourceLeaks = new HashMap<OperatorType, String>() {{
	    put(OperatorType.REACHABILITY,"String dataLeAk%d = java.util.Calendar.getInstance().getTimeZone().getDisplayName();");
	    put(OperatorType.COMPLEXREACHABILITY,"String dataLeAk%d = java.util.Calendar.getInstance().getTimeZone().getDisplayName();");
	    put(OperatorType.SCOPESOURCE,"dataLeAk%d = java.util.Calendar.getInstance().getTimeZone().getDisplayName();");
	    put(OperatorType.TAINTSOURCE,"dataLeAk%d = java.util.Calendar.getInstance().getTimeZone().getDisplayName();");
	}};
	private static HashMap<OperatorType, String> sinkLeaks = new HashMap<OperatorType, String>() {{
	    put(OperatorType.REACHABILITY,"Object throwawayLeAk%d = android.util.Log.d(\"leak-%d\", dataLeAk%d);");
	    put(OperatorType.COMPLEXREACHABILITY,"android.util.Log.d(\"leak-%d\", dataLeAkPath%d);");
	    put(OperatorType.SCOPESINK,"android.util.Log.d(\"leak-%d-%d\", dataLeAk%d);");
	    put(OperatorType.TAINTSINK,"android.util.Log.d(\"leak-%d-%d\", dataLeAk%d);");
	}};
	private static HashMap<OperatorType, String> variableDeclarations = new HashMap<OperatorType, String>() {{
		put(OperatorType.SCOPESOURCE,"String dataLeAk%d = \"%d\";");
	    put(OperatorType.TAINTSOURCE,"String dataLeAk%d = \"\";");
	}};

	/**
	 * Sets the source leak string for based on the operator specified
	 *  
	 * @param op               is the operator type
	 * @param sourceString     is the string to set
	 */
	public static void setSource(OperatorType op, String sourceString) {
		sourceLeaks.replace(getOperatorSource(op), sourceString);
	}

	/**
	 * Sets the sink leak string for based on the operator specified
	 *  
	 * @param op               is the operator type
	 * @param sinkString     is the string to set
	 */
	public static void setSink(OperatorType op, String sinkString) {
		sinkLeaks.replace(getOperatorSink(op), sinkString);
	}
	
	private static OperatorType getOperatorSource(OperatorType op) {
		if(op == OperatorType.SCOPESINK) {
			op = OperatorType.SCOPESOURCE;
		}
		else if(op == OperatorType.TAINTSINK) {
			op = OperatorType.TAINTSOURCE;
		}
		return op;
	}
	
	private static OperatorType getOperatorSink(OperatorType op) {
		if(op == OperatorType.SCOPESOURCE) {
			op = OperatorType.SCOPESINK;
		}
		else if(op == OperatorType.TAINTSOURCE) {
			op = OperatorType.TAINTSINK;
		}
		return op;
	}
	
	/**
	 * Sets the variable declaration leak string for based on the operator specified
	 *  
	 * @param op               is the operator type
	 * @param sinkString     is the string to set
	 */
	public static void setVariableDeclaration(OperatorType op, String sinkString) {
		variableDeclarations.replace(getOperatorSource(op), sinkString);
	}
	
	/**
	 * Formats the sink string and returns the correct sink string based on the
	 * operator type specified.
	 * 
	 * @param op               is the operator type
	 * @param identifier       the identifier for the source
	 * @returns the appropriate source for the operator type specified.
	 */
	public static String getSource(OperatorType op, int identifier) {
		return String.format(sourceLeaks.get(getOperatorSource(op)), identifier);
	}
	
	/**
	 * Formats the sink string and returns the correct sink string based on the
	 * operator type specified.
	 * 
	 * Note that there is no sink defined for the source operator schema, and null
	 * is returned if the schema operator type is specified. Additionally, the sink
	 * operator requires two identifiers to format its sink.
	 * 
	 * @param op               is the operator type
	 * @param sourceIdentifier the identifier for the source
	 * @param sinkIdentifier   the identifier for the sink
	 * @returns the appropriate sink for the operator type specified.
	 */
	public static String getSink(OperatorType op, int sourceIdentifier, int sinkIdentifier) {
		return String.format(sinkLeaks.get(getOperatorSink(op)), sourceIdentifier, sinkIdentifier, sourceIdentifier);
	}
	
	/**
	 * Returns the correct variable declaration string based on the operator type specified.
	 * 
	 * @param op               is the operator type
	 * @returns the appropriate variable declaration for the operator type specified.
	 */
	public static String getVariableDeclaration(OperatorType op) {
		return variableDeclarations.get(getOperatorSource(op));
	}
	
	/**
	 * Returns an unformatted source string based on the operator type specified.
	 * 
	 * @param op               is the operator type
	 */
	public static String getRawSource(OperatorType op) {
		return sourceLeaks.get(getOperatorSource(op));
	}

	/**
	 * Returns an unformatted sink string based on the operator type specified.
	 * 
	 * @param op               is the operator type
	 */
	public static String getRawSink(OperatorType op) {
		return sinkLeaks.get(getOperatorSink(op));
	}

	/**
	 * Formats the leak string and returns it. Accepts only OperatorType Reachability and ComplexReachabilityOperator
	 * 
	 * @param identifier an instance of the global counter utility used to identify
	 *                   the leak string
	 * @param type       OperatorType
	 * 
	 * @returns the string version of a data leak as used by the reachability
	 *          operator schema.
	 */

	public static String getLeak(OperatorType op, int identifier) {
		if (op == OperatorType.REACHABILITY)
			return String.format(sourceLeaks.get(op), identifier) + "\n"
			+ String.format(sinkLeaks.get(op), identifier, identifier, identifier);
		else if (op == OperatorType.COMPLEXREACHABILITY) {

			String[] paths = new String[] {
					"String[] leakArRay%d = new String[] {\"n/a\", dataLeAk%d};\n"
							+ "String dataLeAkPath%d = leakArRay%d[leakArRay%d.length - 1];",
					"java.util.HashMap<String, java.util.HashMap<String, String>> leakMaP%d = new java.util.HashMap<String, java.util.HashMap<String, String>>();\n"
							+ "leakMaP%d.put(\"test\", new java.util.HashMap<String, String>());\n"
							+ "leakMaP%d.get(\"test\").put(\"test\", dataLeAk%d);\n"
							+ "String dataLeAkPath%d = leakMaP%d.get(\"test\").get(\"test\");",
					"StringBuffer leakBuFFer%d = new StringBuffer();" + "for (char chAr%d : dataLeAk%d.toCharArray()) {"
							+ "leakBuFFer%d.append(chAr%d);" + "}" + "String dataLeAkPath%d = leakBuFFer%d.toString();",
					"String dataLeAkPath%d;" + "try {" + "throw new Exception(dataLeAk%d);"
							+ "} catch (Exception leakErRor%d) {" + "dataLeAkPath%d = leakErRor%d.getMessage();"
							+ "}" };
			String source = getSource(OperatorType.COMPLEXREACHABILITY, identifier);
			String sink = String.format(getRawSink(OperatorType.COMPLEXREACHABILITY), identifier, identifier);
			String leak = source + "\n" + String.format(paths[identifier % paths.length], identifier, identifier,
					identifier, identifier, identifier, identifier, identifier) + "\n" + sink;
			return leak;

		}
		else {
			throw new IllegalArgumentException("Type must be Operator.REACHABILITY or Operator.COMPLEXREACHABILITY ");
		}
	}

	/**
	 * Hopping logic that adds a level of misdirection that certain security
	 * analysis tools can't follow.
	 * 
	 * @returns the "hop" that results in one dataleak string being set equal to
	 *          another.
	 */
	public static String getHop(int identifierOne, int identifierTwo) {
		return String.format("String dataLeAk%d = dataLeAk%d", identifierOne, identifierTwo);
	}

}
