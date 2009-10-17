package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

public class AccessAnalyzerForImmutability extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions, blah;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	
	public AccessAnalyzerForImmutability(
			MakeImmutableRefactoring makeImmutableRefactoring,
			ICompilationUnit unit, ASTRewrite rewriter) {
		this.refactoring = makeImmutableRefactoring;
		this.rewriter = rewriter;
		this.astRoot = rewriter.getAST();
		status = new RefactoringStatus();
		groupDescriptions = new ArrayList<TextEditGroup>();
	}
	
	@Override
	public boolean visit(FieldDeclaration fieldDecl) {
		if (doesParentBindToTargetClass(fieldDecl)) {
			
			// Change modifier to final
			List modifiers = fieldDecl.modifiers();
			if (!Flags.isFinal(fieldDecl.getModifiers())) {
				int finalModifiers = fieldDecl.getModifiers() | ModifierKeyword.FINAL_KEYWORD.toFlagValue();
				TextEditGroup gd = new TextEditGroup("change to final");
				ModifierRewrite.create(rewriter, fieldDecl).setModifiers(finalModifiers, gd);
				groupDescriptions.add(gd);
			}
			
			// Add initializers to the fragments that do not have them
			Type fieldDeclType = fieldDecl.getType(); 
			List fragments = fieldDecl.fragments();
			for (Object obj : fragments) {
				VariableDeclarationFragment frag = (VariableDeclarationFragment)obj;
				if (frag != null && frag.getInitializer() == null) {
					VariableDeclarationFragment newFrag = (VariableDeclarationFragment)ASTNode.copySubtree(frag.getAST(), frag);
					Expression initializer = null;
					if (fieldDeclType instanceof PrimitiveType) {
						PrimitiveType primType = (PrimitiveType)fieldDeclType;
						
						if (primType.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN) {
							initializer = newFrag.getAST().newBooleanLiteral(false);
						}
						else if (primType.getPrimitiveTypeCode() == PrimitiveType.CHAR) {
							CharacterLiteral charLit = newFrag.getAST().newCharacterLiteral(); 
							charLit.setCharValue('\u0000');
								
							initializer = charLit;
						}
						else if (primType.getPrimitiveTypeCode() == PrimitiveType.FLOAT) {
							initializer = newFrag.getAST().newNumberLiteral("0.0f");
						}
						else if (primType.getPrimitiveTypeCode() == PrimitiveType.DOUBLE) {
							initializer = newFrag.getAST().newNumberLiteral("0.0d");
						}
						else if (   primType.getPrimitiveTypeCode() == PrimitiveType.INT
						         || primType.getPrimitiveTypeCode() == PrimitiveType.SHORT
						         || primType.getPrimitiveTypeCode() == PrimitiveType.BYTE) {
					 		initializer = newFrag.getAST().newNumberLiteral("0");
						}
						else if (primType.getPrimitiveTypeCode() == PrimitiveType.LONG) {
							initializer = newFrag.getAST().newNumberLiteral("0L");
						}
						else {
							continue; // TODO add assertion as this should not happen
						}
					}
					else if (fieldDeclType instanceof SimpleType) {
						SimpleType simpType = (SimpleType)fieldDeclType;
						assert simpType != null;
						
						// NOTE Not sure whether it is a good idea to handle strings unlike other objects
						initializer = (simpType.getName().toString().equals("String")) 
						            ? newFrag.getAST().newStringLiteral()
						            : newFrag.getAST().newNullLiteral();
					}
					else {
						continue; // TODO: not supported failure
					}
					
					if (initializer != null) {
						newFrag.setInitializer(initializer);
					
						TextEditGroup gd = new TextEditGroup("add initializer");
						rewriter.replace(frag, newFrag, gd);
						groupDescriptions.add(gd);
					}
				}
			}
		}
		return false;
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
	
	public Collection getGroupDescriptions() {
		return groupDescriptions;
	}

}

class A {
	int i = 10, j = 12;
}
