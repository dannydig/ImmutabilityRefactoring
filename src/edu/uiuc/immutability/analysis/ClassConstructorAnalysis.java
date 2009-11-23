package edu.uiuc.immutability.analysis;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Does the following checks on the constructors:
 * - Checks if a full constructor exists (a constructor that initializes all the fields)  
 */
public class ClassConstructorAnalysis extends ASTVisitor {
	public enum FullConstructorStatus {
		HAS_NOT_FULL_CONSTRUCTOR,
		HAS_FULL_CONSTRUCTOR,
		FULL_CONSTRUCTOR_INVALID
	}
	
	private FullConstructorStatus fullConstructorStatus = FullConstructorStatus.HAS_NOT_FULL_CONSTRUCTOR;
	List<Type> fieldTypes;
	private AnalysisUtil analysisUtil;
	
	public ClassConstructorAnalysis(TypeDeclaration targetClass) {
		fieldTypes = getFieldTypes(targetClass);
		
		analysisUtil = new AnalysisUtil();
	}
	
	public FullConstructorStatus getFullConstructorStatus() {
		return fullConstructorStatus;
	}
	

	@Override
	public boolean visit(MethodDeclaration methodDecl) {
		if (methodDecl.isConstructor() && analysisUtil.doesMethodTakeTypes(methodDecl, fieldTypes)) {
			fullConstructorStatus = FullConstructorStatus.HAS_FULL_CONSTRUCTOR;
		}
		
		return false;
	}

	@SuppressWarnings("unchecked")
	private List<Type> getFieldTypes(TypeDeclaration targetClass) {
		List<Type> fieldTypes = new ArrayList<Type>(); 
		
		FieldDeclaration[] fields = targetClass.getFields();
		for (FieldDeclaration field : fields) {
			List fragments = field.fragments();
			for (Object fragmentObject : fragments) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
				assert fragment != null;
				
				fieldTypes.add(field.getType());
			}
		}
		
		return fieldTypes;
	}
}
