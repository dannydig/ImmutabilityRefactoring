package edu.uiuc.immutability;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class MakeClassImmutableRewriter {
	private MakeClassImmutableVisitor immutableRewriter;
	
	private final MakeImmutableRefactoring makeImmutableRefactoring;
	private final ICompilationUnit unit;
	private final ASTRewrite rewriter;

	
	public MakeClassImmutableRewriter(MakeImmutableRefactoring makeImmutableRefactoring,
	                                  ICompilationUnit unit, 
	                                  ASTRewrite rewriter) {
		this.makeImmutableRefactoring = makeImmutableRefactoring;
		this.unit = unit;
		this.rewriter = rewriter;
	}

	public void rewrite(TypeDeclaration targetClass, ClassMutatorAnalysis mutatorAnalysis) {
		immutableRewriter =  new MakeClassImmutableVisitor(makeImmutableRefactoring, unit, rewriter, mutatorAnalysis);

		targetClass.accept(immutableRewriter);
	}

	public RefactoringStatus getStatus() {
		return (immutableRewriter != null) ? immutableRewriter.getStatus(): null;
	}

}
