package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

@SuppressWarnings("restriction")
public class AccessAnalyzerForImmutability extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	private final ICompilationUnit unit;
	
	public AccessAnalyzerForImmutability(
			MakeImmutableRefactoring makeImmutableRefactoring,
			ICompilationUnit unit, ASTRewrite rewriter) {
		this.refactoring = makeImmutableRefactoring;
		this.rewriter = rewriter;
		this.astRoot = rewriter.getAST();
		this.unit = unit;
		status = new RefactoringStatus();
		groupDescriptions = new ArrayList<TextEditGroup>();
	}
	
	@Override
	public boolean visit(MethodDeclaration methodDecl) {
		if (doesParentBindToTargetClass (methodDecl)) {
			// TODO get lighweight node from heavyweight node
			// apply search engine
			//if (positive){
				// do the rewrite
			//}
			
		}
		return false;
	}
	
	private boolean doesParentBindToTargetClass(MethodDeclaration node) {
		// TODO Auto-generated method stub
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean visit(FieldDeclaration fieldDecl) {
		if (doesParentBindToTargetClass(fieldDecl)) {
						
			// Change modifier to final
			if (!Flags.isFinal(fieldDecl.getModifiers())) {
				TextEditGroup gd = new TextEditGroup("change field to final and add initializer(s)");

				int finalModifiers = fieldDecl.getModifiers() | ModifierKeyword.FINAL_KEYWORD.toFlagValue();
				ModifierRewrite.create(rewriter, fieldDecl).setModifiers(finalModifiers, gd);
				
				// Add initializers to the fragments that do not have them as they are required for final variables
				Type fieldDeclType = fieldDecl.getType(); 
				List fragments = fieldDecl.fragments();
				for (Object obj : fragments) {
					VariableDeclarationFragment frag = (VariableDeclarationFragment)obj;
					
					// Check whether the field is already initialized
					if (frag != null && frag.getInitializer() == null) {
						
						// Check whether the field is already initialized in a constructor in which case we can't
						// initialize it a second time at the declaration point
						if ( !isFieldInitializedInConstructor(fieldDeclType, frag, gd) ) {
	
							// Add initializer
							Expression initializer = createDefaultInitializer(fieldDeclType);
							if (initializer == null) continue;
							
							VariableDeclarationFragment newFrag =
									(VariableDeclarationFragment)ASTNode.copySubtree(astRoot, frag);
							newFrag.setInitializer(initializer);
							rewriter.replace(frag, newFrag, gd);
						}
					}
				}
				
				groupDescriptions.add(gd);
			}
		}
		return false;
	}
	
	private Expression createDefaultInitializer(Type fieldDeclType) {
		Expression initializer = null;
		
		if (fieldDeclType instanceof PrimitiveType) {
			PrimitiveType primType = (PrimitiveType)fieldDeclType;
			
			if (primType.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN) {
				initializer = astRoot.newBooleanLiteral(false);
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.CHAR) {
				CharacterLiteral charLit = astRoot.newCharacterLiteral(); 
				charLit.setCharValue('\u0000');
					
				initializer = charLit;
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.FLOAT) {
				initializer = astRoot.newNumberLiteral("0.0f");
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.DOUBLE) {
				initializer = astRoot.newNumberLiteral("0.0d");
			}
			else if (   primType.getPrimitiveTypeCode() == PrimitiveType.INT
			         || primType.getPrimitiveTypeCode() == PrimitiveType.SHORT
			         || primType.getPrimitiveTypeCode() == PrimitiveType.BYTE) {
		 		initializer = astRoot.newNumberLiteral("0");
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.LONG) {
				initializer = astRoot.newNumberLiteral("0L");
			}
			else {
				// Should never happen
				assert false;
			}
		}
		else if (fieldDeclType instanceof SimpleType) {
			SimpleType simpType = (SimpleType)fieldDeclType;
			assert simpType != null;
			
			// NOTE Not sure whether it is a good idea to handle strings unlike other objects
			initializer = (simpType.getName().toString().equals("String")) 
			            ? astRoot.newStringLiteral()
			            : astRoot.newNullLiteral();
		}

		
		return initializer;
	}
	
	@SuppressWarnings("unchecked")
	private boolean isFieldInitializedInConstructor(Type fieldDeclType, VariableDeclarationFragment frag, TextEditGroup gd) {
		boolean isInitializedInConstructor = false;
		
		// Get the class of the variable (The grandparent of a field/fragment is always its class)
		TypeDeclaration parentClass = (TypeDeclaration)frag.getParent().getParent();
		assert parentClass.isInterface() == false; //Interfaces can't have non-static fields
		
		// Go through all the constructors and check whether they initialize the variable
		IType parentClassType = unit.getType(parentClass.getName().toString());
		IField field = parentClassType.getField(frag.getName().toString());
		
		MethodDeclaration[] methodDecls;
		IMethod[] methods;
		try {
			List<MethodDeclaration> methodDeclsWithoutInitialization = new ArrayList<MethodDeclaration>();
		
			// We take advantage of the fact that the method lists of both IType and TypeDeclaration are ordered if
			// it is a source file (which are the only files we transform) to build a list of the MethodDeclarations
			// of constructors that don't initialize the current field
			methodDecls = parentClass.getMethods();
			methods = parentClassType.getMethods();
			assert methodDecls.length == methods.length;
			for (int i = 0; i < methods.length; ++i ) {
				IMethod method = methods[i];
				MethodDeclaration methodDecl = methodDecls[i];
				
				if (method.isConstructor()) {
					final List matches = getWritesToFieldInMethod(field, method);
					if (!matches.isEmpty()) {
						isInitializedInConstructor = true;
					}
					else {
						methodDeclsWithoutInitialization.add(methodDecl);
					}
				}
			}
			
			if ( isInitializedInConstructor ) {

				// At least one constructor initializes the variable so it won't be initialized in the declaration
				// and we must add initialization statements to any constructors that lack it
				for ( MethodDeclaration methodDecl : methodDeclsWithoutInitialization ) {
					
					// Create an assignment of the default value to frag in methodDecl
					Assignment assignment = astRoot.newAssignment();
					//LHS
					FieldAccess fieldAccess = astRoot.newFieldAccess();
					ThisExpression thisExpr = astRoot.newThisExpression();
					fieldAccess.setExpression(thisExpr);
					SimpleName fieldName = astRoot.newSimpleName(frag.getName().toString()); 
					fieldAccess.setName(fieldName);
					assignment.setLeftHandSide(fieldAccess);
					//OP
					assignment.setOperator(Assignment.Operator.ASSIGN);
					//RHS
					Expression initializer = createDefaultInitializer(fieldDeclType);
					assignment.setRightHandSide(initializer);
					
					// Wrap the assignment in an assign statement
					ExpressionStatement assignStmt = astRoot.newExpressionStatement(assignment);
					
					// Add the assign statement to the method body
					rewriter.getListRewrite(methodDecl.getBody(), Block.STATEMENTS_PROPERTY).insertLast(assignStmt, gd);
				}
				
				return true;
			}
			
		} catch (JavaModelException e) {
			// TODO: Trigger error
		} catch (CoreException e) {
			// TODO: Trigger error
		}
		
		return false;
	}

	private List getWritesToFieldInMethod(IField field, IMethod method)
			throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(field, IJavaSearchConstants.WRITE_ACCESSES);
		SearchEngine engine = new SearchEngine();
		SearchParticipant[] participants = new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { method });
		
		final List matches = new ArrayList();
		SearchRequestor requestor = new SearchRequestor() {
			public void acceptSearchMatch(SearchMatch match) {
				matches.add(match);
			}
		};
		
		engine.search(pattern, participants, scope, requestor, null);
		return matches;
	}
	
	private boolean doesParentBindToTargetClass(FieldDeclaration fieldDecl) {
		ASTNode parent = ASTNodes.getParent(fieldDecl, TypeDeclaration.class);
		if (parent != null) {
			TypeDeclaration typeDecl = (TypeDeclaration) parent;
			return Bindings.equals(typeDecl.resolveBinding(), refactoring.getTargetBinding());
		}
		return false;
	}
	

	public RefactoringStatus getStatus() {
		// TODO Auto-generated method stub
		return status;
	}
	
	@SuppressWarnings("unchecked")
	public Collection getGroupDescriptions() {
		return groupDescriptions;
	}

}
