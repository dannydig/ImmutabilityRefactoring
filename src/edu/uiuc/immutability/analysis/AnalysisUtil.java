package edu.uiuc.immutability.analysis;

import java.util.List;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

public class AnalysisUtil {

	@SuppressWarnings("unchecked")
	public boolean doesMethodTakeTypes(MethodDeclaration methodDecl, List<Type> types) {
		List parameters = methodDecl.parameters();
		if (parameters.size() == types.size() ) {
			boolean typesEqual = true;
			
			for (int i = 0; i < parameters.size(); i++) {
				SingleVariableDeclaration param = (SingleVariableDeclaration)parameters.get(i);
				assert param != null;
				
				if (!param.getType().toString().equals(types.get(i).toString())) {
					typesEqual = false;
					break;
				}
			}
			
			if (typesEqual) return true;
		}
		
		return false;
	}
}
