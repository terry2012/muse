package edu.wm.cs.muse;

//import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
//import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.omg.CORBA.DynAnyPackage.TypeMismatch;

import edu.wm.cs.muse.dataleak.support.InvalidTypeException;
//import edu.wm.cs.muse.dataleak.schemas.TaintSchema;
import edu.wm.cs.muse.dataleak.support.SchemaOperatorUtility;

// class for experimenting with and understanding other schemas
public class TempSchema extends ASTVisitor {

	@Override
	public boolean visit(MethodDeclaration node) {
//		System.out.println(node.toString());
		try {
			System.out.println(node.getName() +" " +SchemaOperatorUtility.getMethodDepthInternalClass(node));
		} catch (InvalidTypeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		System.out.println(node.getParent().getNodeType() == ASTNode.TYPE_DECLARATION);
		return true;
	}

}
