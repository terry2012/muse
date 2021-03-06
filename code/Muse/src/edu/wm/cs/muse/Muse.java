package edu.wm.cs.muse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import edu.wm.cs.muse.dataleak.operators.ComplexReachability;
import edu.wm.cs.muse.dataleak.operators.ReachabilityOperator;
import edu.wm.cs.muse.dataleak.operators.SinkOperator;
import edu.wm.cs.muse.dataleak.operators.SourceOperator;
import edu.wm.cs.muse.dataleak.operators.TaintOperator;
import edu.wm.cs.muse.dataleak.operators.TaintSinkOperator;
import edu.wm.cs.muse.dataleak.schemas.ComplexReachabilitySchema;
import edu.wm.cs.muse.dataleak.schemas.ReachabilitySchema;
import edu.wm.cs.muse.dataleak.schemas.SinkSchema;
import edu.wm.cs.muse.dataleak.schemas.SourceSchema;
import edu.wm.cs.muse.dataleak.schemas.TaintSchema;
import edu.wm.cs.muse.dataleak.schemas.TaintSinkSchema;
import edu.wm.cs.muse.dataleak.support.Arguments;
import edu.wm.cs.muse.dataleak.support.FileUtility;
import edu.wm.cs.muse.dataleak.support.OperatorType;
import edu.wm.cs.muse.mdroid.ASTHelper;

/**
 *
 * @author Richard Bonett
 * @since October 12, 2017
 */
public class Muse {

	ASTRewrite rewriter;
	// TODO: Does not handle anonymous declarations and try_catch clauses well.
	// currently just ignores such methods.
	// TODO: Make schema for inserting leaks in static methods, since regular
	// operators won't work well with static ones?
	// TODO: does not handle enum well since enum methods are considered static and
	// not detected in java ast as static

	public void runMuse(String[] args) throws MalformedTreeException, BadLocationException {
		// Usage Error
		if (args.length != 5) {
			printArgumentError();
			return;
		}

		Arguments.extractArguments(args);

		FileUtility.setupMutantsDirectory();

		System.out.println(Arguments.getRootPath());

		Collection<File> files = FileUtils.listFiles(new File(Arguments.getRootPath()), TrueFileFilter.INSTANCE,
				TrueFileFilter.INSTANCE);

		for (File file : files) {
			try {
				if (file.getName().endsWith(".java")
						&& file.getCanonicalPath().contains(Arguments.getAppName().replace(".", "/"))
						&& !file.getName().contains("EmmaInstrumentation.java")
						&& !file.getName().contains("FinishListener.java")
						&& !file.getName().contains("InstrumentedActivity.java")
						&& !file.getName().contains("InstrumentedTest.java")
						&& !file.getName().contains("UnitTest.java")
						&& !file.getName().contains("SMSInstrumentedReceiver.java")) {
					System.out.println("In file: " + file.getName());
					// System.out.println("PROCESSING: " + file.getAbsolutePath());
					String source = FileUtility.readSourceFile(file.getAbsolutePath()).toString();

					// Creates an abstract syntax tree.
					CompilationUnit root = ASTHelper.getAST(source, Arguments.getBinariesFolder(),
							Arguments.getRootPath());

					// Creates a new instance for describing manipulations of the given AST.
					rewriter = ASTRewrite.create(root.getAST());

					operatorExecution(root, rewriter, source, file, getOperatorType(Arguments.getOperator()));

				}
			} catch (IOException e) {
				System.err
						.println(String.format("ERROR PROCESSING \"%s\": %s", file.getAbsolutePath(), e.getMessage()));
				return;
			}
		}
	}

	private OperatorType getOperatorType(String inputOperator) {
		// SOURCE, SINK, TAINT, TAINTSINK and REACHABILITY
		System.out.println("Input operator: " + inputOperator);
		switch (inputOperator) {
		case "SOURCE":
			return OperatorType.SOURCE;
		case "SINK":
			return OperatorType.SINK;
		case "TAINT":
			return OperatorType.TAINT;
		case "TAINTSINK":
			return OperatorType.TAINTSINK;
		case "REACHABILITY":
			return OperatorType.REACHABILITY;
		case "COMPLEXREACHABILITY":
			return OperatorType.COMPLEXREACHABILITY;
		default:
			printArgumentError();
			System.exit(-1);
			break;
		}
		return null;
	}

	/**
	 * Converts all modifications recorded by this rewriter into an object
	 * representing the the corresponding text edits to the source of a ITypeRoot
	 * from which the AST was created from. The type root's source itself is not
	 * modified by this method call.
	 * 
	 * @author Amit Seal Ami
	 * @param file   where it will be written
	 * @param source is the content of source
	 * @throws BadLocationException
	 * @throws IOException
	 */
	private void applyChangesToFile(File file, String source, ASTRewrite rewriter)
			throws BadLocationException, IOException {
		Document sourceDoc = new Document(source);

		TextEdit edits = rewriter.rewriteAST(sourceDoc, null);
		// Applies the edit tree rooted by this edit to the given document.
		edits.apply(sourceDoc);
		FileUtils.writeStringToFile(file, sourceDoc.get(), false);
		rewriter = null;
	}

	/**
	 * Uses the rewriter to create an AST for the schema to utilize then creates a
	 * new instance to manipulate the AST. The root node then accepts the schema
	 * visitor on the visit. The rewriter implements the modifications changes made
	 * by the operator to the AST
	 * 
	 * @param root         is the compilation unit based root of the AST
	 * @param rewriter     ASTRewrite object that holds the changes to the AST
	 * @param file
	 * @param source
	 * @param operatorType is the type of operator being executed: sink, source,
	 *                     reachability, or taint
	 * @throws IOException
	 * @throws BadLocationException
	 * @throws MalformedTreeException
	 */
	public void operatorExecution(CompilationUnit root, ASTRewrite rewriter, String source, File file,
			OperatorType operatorType) throws MalformedTreeException, BadLocationException, IOException {

		File temp_file;
		String newSource;
		CompilationUnit newRoot;
		switch (operatorType) {
		case SINK:
			SourceSchema sourceSchema_s = new SourceSchema();
			root.accept(sourceSchema_s);
			SourceOperator sourceOperator_s = new SourceOperator(rewriter, sourceSchema_s.getNodeChanges());
			rewriter = sourceOperator_s.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			String sink_temp_file_path = "test/temp/temp_file.java";
			temp_file = new File(sink_temp_file_path);
			tempFileWriter(root, rewriter, source, temp_file);

			newSource = FileUtility.readSourceFile(sink_temp_file_path).toString();
			newRoot = ASTHelper.getAST(newSource, Arguments.getBinariesFolder(), "test/temp/");
			rewriter = null;
			root = newRoot;
			source = newSource;
			rewriter = ASTRewrite.create(root.getAST());
			SinkSchema sinkSchema = new SinkSchema();
			root.accept(sinkSchema);
			SinkOperator sinkOperator = new SinkOperator(rewriter, sinkSchema.getNodeChanges());
			rewriter = sinkOperator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			Files.delete(temp_file.toPath());
			break;

		case SOURCE:
			SourceSchema sourceSchema = new SourceSchema();
			root.accept(sourceSchema);
			SourceOperator sourceOperator = new SourceOperator(rewriter, sourceSchema.getNodeChanges());
			rewriter = sourceOperator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			break;

		case REACHABILITY:
			ReachabilitySchema reachabilitySchema = new ReachabilitySchema();
			root.accept(reachabilitySchema);
			ReachabilityOperator reachabilityOperator = new ReachabilityOperator(rewriter,
					reachabilitySchema.getNodeChanges());
			rewriter = reachabilityOperator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			break;

		case TAINT:
			TaintSchema taintSchema = new TaintSchema();
			root.accept(taintSchema);
			TaintOperator taintOperator = new TaintOperator(rewriter, taintSchema.getNodeChanges());
			rewriter = taintOperator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			break;

		case TAINTSINK:
			TaintSchema taintSchema_ts = new TaintSchema();
			root.accept(taintSchema_ts);
			TaintOperator taintOperator_ts = new TaintOperator(rewriter, taintSchema_ts.getNodeChanges());
			rewriter = taintOperator_ts.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			String taintsink_temp_file_path = "test/temp/temp_file_taintsink.java";
			temp_file = new File(taintsink_temp_file_path);
			tempFileWriter(root, rewriter, source, temp_file);

			newSource = FileUtility.readSourceFile(taintsink_temp_file_path).toString();
			newRoot = ASTHelper.getAST(newSource, Arguments.getBinariesFolder(), "test/temp/");
			rewriter = null;
			root = newRoot;
			source = newSource;
			rewriter = ASTRewrite.create(root.getAST());

			TaintSinkSchema taintSinkSchema = new TaintSinkSchema();
			root.accept(taintSinkSchema);
			TaintSinkOperator operator = new TaintSinkOperator(rewriter, taintSinkSchema.getFieldNodeChanges(),
					taintSinkSchema.getMethodNodeChanges());
			rewriter = operator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			Files.delete(temp_file.toPath());
			break;
		case COMPLEXREACHABILITY:
			ComplexReachabilitySchema complexSchema = new ComplexReachabilitySchema();
			root.accept(complexSchema);
			ComplexReachability complexOperator = new ComplexReachability(rewriter, complexSchema.getNodeChanges());
			rewriter = complexOperator.InsertChanges();
			applyChangesToFile(file, source, rewriter);
			break;

		}
	}

	public ASTRewrite tempFileWriter(CompilationUnit root, ASTRewrite rewriter, String source, File file)
			throws MalformedTreeException, BadLocationException, IOException {
		File temp_file = new File("test/temp/temp_file.java");

		// Applies the edit tree rooted by this edit to the given document.
		// edits.apply(sourceDoc);
		Document tempDocument = new Document(source);
		TextEdit tempEdits = rewriter.rewriteAST(tempDocument, null);

		tempEdits.apply(tempDocument);
		FileUtils.writeStringToFile(temp_file, tempDocument.get(), false);
		FileUtils.writeStringToFile(file, tempDocument.get(), false);

		// TaintSinkSchema
		source = FileUtility.readSourceFile(temp_file.getAbsolutePath()).toString();
		rewriter = null;
		root = ASTHelper.getAST(tempDocument.get(), Arguments.getBinariesFolder(), "test/temp/");
		return rewriter = ASTRewrite.create(root.getAST());
	}

	public ASTRewrite tempExecution(CompilationUnit root, ASTRewrite rewriter) {

		TempSchema tempSchema = new TempSchema();
		root.accept(tempSchema);
		return rewriter;
	}

	private void printArgumentError() {
		System.out.println("******* ERROR: INCORRECT USAGE *******");
		System.out.println("Argument List:");
		System.out.println("1. Binaries path");
		System.out.println("2. App Source Code path");
		System.out.println("3. App Name");
		System.out.println("4. Mutants path");
		System.out.println("5. MutationScheme: SOURCE, SINK, TAINT, TAINTSINK and REACHABILITY (caseSensitive).");
	}

	public static void main(String[] args) throws MalformedTreeException, BadLocationException {
		new Muse().runMuse(args);
	}
}