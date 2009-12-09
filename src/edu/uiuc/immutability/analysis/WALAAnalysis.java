package edu.uiuc.immutability.analysis;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.demandpa.alg.ContextSensitiveStateMachine;
import com.ibm.wala.demandpa.alg.DemandRefinementPointsTo;
import com.ibm.wala.demandpa.alg.ThisFilteringHeapModel;
import com.ibm.wala.demandpa.alg.statemachine.StateMachineFactory;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel;
import com.ibm.wala.demandpa.util.MemoryAccessMap;
import com.ibm.wala.demandpa.util.PABasedMemoryAccessMap;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.strings.Atom;


public class WALAAnalysis {
	protected static final int N_CFA = 1;

	protected IProgressMonitor monitor;

	protected AnalysisScope scope;
	protected ClassHierarchy cha;
	protected CallGraph callGraph;
	protected DemandRefinementPointsTo demandPointsTo;

	protected Iterable<Entrypoint> entrypoints;


	public void setEntrypoints(Set<EntrypointData> entrypointsData) {
		String[] classNames = new String[entrypointsData.size()];
		String[] methodNames = new String[entrypointsData.size()];
		String[] methodDescriptors = new String[entrypointsData.size()];

		int i = 0;
		for (EntrypointData entrypointData : entrypointsData) {
			classNames[i] = entrypointData.getClassName();
			methodNames[i] = entrypointData.getMethodName();
			methodDescriptors[i] = entrypointData.getMethodDescriptor();

//			System.out.println(classNames[i] + ", "
//					+ methodNames[i] + ", "
//					+ methodDescriptors[i]);

			i++;
		}
		System.out.println("Added " + i + " entrypoints.");

		entrypoints = makeEntrypoints(scope, cha, classNames, methodNames, methodDescriptors);
	}

	public void buildCallGraph() throws IllegalArgumentException,
	CallGraphBuilderCancelException {
		AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

		final AnalysisCache analysisCache = new AnalysisCache();

		// Create a context-sensitive call graph
		ContextSelector contextSelector = new nCFAContextSelector(N_CFA, new DefaultContextSelector(options));
		CallGraphBuilder cgBuilder = Util.makeVanillaZeroOneCFABuilder(options, analysisCache, cha, scope, contextSelector, null);

		callGraph = cgBuilder.makeCallGraph(options, monitor);

		// MemoryAccessMap mam = new SimpleMemoryAccessMap(cg, cgBuilder.getPointerAnalysis().getHeapModel(), false);
		MemoryAccessMap mam = new PABasedMemoryAccessMap(callGraph, cgBuilder.getPointerAnalysis());
		SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(options, analysisCache, cha, scope);

		demandPointsTo = new DemandRefinementPointsTo(callGraph, new ThisFilteringHeapModel(builder, cha), mam,
				cha, options, getStateMachineFactory());

		demandPointsTo.setRefinementPolicyFactory(new PARefinementPolicyFactory());

		
		// Other refinement policies:

		//		fullDemandPointsTo.setRefinementPolicyFactory(
		//				new SinglePassRefinementPolicy.Factory(new AlwaysRefineFieldsPolicy(), new AlwaysRefineCGPolicy()));
		//		fullDemandPointsTo.setRefinementPolicyFactory(
		//				new ManualRefinementPolicy.Factory(cha));
		//		fullDemandPointsTo.setRefinementPolicyFactory(
		//				new TunedRefinementPolicy.Factory(cha));
	}

	protected StateMachineFactory<IFlowLabel> getStateMachineFactory() {
		return new ContextSensitiveStateMachine.Factory();
	}

	////////////////////
	// Get PointsTo sets

	protected Set<InstanceKey> getPointsToSet(String markerName, String markerMethodName) {
		CGNode markerMethod = findMethod(demandPointsTo.getBaseCallGraph(), markerMethodName);

		return getPointsToSet(markerName, markerMethod);
	}

	public Set<InstanceKey> getPointsToSet(String markerName, CGNode markerMethod) {
		PointerKey keyToQuery = getParam(markerMethod, markerName, demandPointsTo.getHeapModel());
		Collection<InstanceKey> pointsTo = demandPointsTo.getPointsTo(keyToQuery);

//		System.out.println(markerName + ": ");
//		for (InstanceKey ik : pointsTo)
//			System.out.println(ik);

		return new HashSet<InstanceKey>(pointsTo);
	}

	public static PointerKey getParam(CGNode n, String methodName, HeapModel heapModel) {
		IR ir = n.getIR();
		for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
			SSAInstruction s = it.next();
			if (s instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction call = (SSAInvokeInstruction) s;
				if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)) {
					IntSet indices = ir.getCallInstructionIndices(((SSAInvokeInstruction) s).getCallSite());
					Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
					SSAInstruction callInstr = ir.getInstructions()[indices.intIterator().next()];
					Assertions.productionAssertion(callInstr.getNumberOfUses() == 1, "multiple uses for call");
					return heapModel.getPointerKeyForLocal(n, callInstr.getUse(0));
				}
			}
		}
		Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
		return null;
	}


	////////////////////////////
	// find method in call graph

	public static CGNode findMethod(CallGraph cg, String method) {
		return findMethod(cg, method, null);
	}

	/**
	 * Returns the call graph node for the method, specified by name and descriptor string
	 * e.g.
	 * public static void main(String[] args) has:
	 * 		name: main
	 * 		descriptor: ([Ljava/lang/String;)V
	 * 
	 * If descriptor is null, any method description is valid.
	 */
	public static CGNode findMethod(CallGraph cg, String method, String descriptor) {
		// To check for a method from a particular class:
		// n.getMethod().getDeclaringClass().equals(declaringClass) 
		Descriptor d = null;
		if (descriptor != null)
			d = Descriptor.findOrCreateUTF8(descriptor);

		Atom name = Atom.findOrCreateUnicodeAtom(method);
		// To check only methods in the entry point class, use:
		// for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext();) {
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			if (n.getMethod().getName().equals(name)
					&& (d == null || n.getMethod().getDescriptor().equals(d))) {
				return n;
			}
		}
		Assertions.UNREACHABLE("failed to find method");
		return null;
	}


	//////////////////////
	// make entrypoint set

	public static Iterable<Entrypoint> makeEntrypoints(AnalysisScope scope, final IClassHierarchy cha,
			String className, String methodName, String methodDescriptor) {
		return makeEntrypoints(scope, cha, new String[] { className },
				new String[] { methodName }, new String[] { methodDescriptor });
	}

	public static Iterable<Entrypoint> makeEntrypoints(final AnalysisScope scope, final IClassHierarchy cha,
			final String[] classNames, final String[] methodNames, final String[] methodDescriptors) {
		if (scope == null) {
			throw new IllegalArgumentException("scope is null");
		}

		return makeEntrypoints(scope.getApplicationLoader(), cha, classNames, methodNames, methodDescriptors);
	}

	public static Iterable<Entrypoint> makeEntrypoints(final ClassLoaderReference loaderRef, final IClassHierarchy cha,
			final String[] classNames, final String[] methodNames, final String[] methodDescriptors)
			throws IllegalArgumentException, IllegalArgumentException, IllegalArgumentException {

		if (classNames == null || methodNames == null || methodDescriptors == null) {
			throw new IllegalArgumentException("classNames == null || methodNames == null || methodDescriptors == null");
		}
		if (classNames.length != methodNames.length || classNames.length != methodDescriptors.length) {
			throw new IllegalArgumentException("classNames.length != methodNames.length != methodDescriptors.length");
		}
		if (classNames.length == 0) {
			throw new IllegalArgumentException("classNames.length == 0");
		}
		if (classNames[0] == null && 0 < classNames.length) {
			throw new IllegalArgumentException("(0 < classNames.length) and (classNames[0] == null)");
		}

		for (int i = 0; i < classNames.length; i++) {
			if (classNames[i].indexOf("L") != 0) {
				throw new IllegalArgumentException("Expected class name to start with L " + classNames[i]);
			}
			if (classNames[i].indexOf(".") > 0) {
				Assertions.productionAssertion(false, "Expected class name formatted with /, not . " + classNames[i]);
			}
		}

		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				return new Iterator<Entrypoint>() {
					private int index = 0;

					public void remove() {
						Assertions.UNREACHABLE();
					}

					public boolean hasNext() {
						return index < classNames.length;
					}

					public Entrypoint next() {
						Atom mainMethod = Atom.findOrCreateAsciiAtom(methodNames[index]);
						TypeReference T = TypeReference.findOrCreate(loaderRef, TypeName.string2TypeName(classNames[index]));
						MethodReference mainRef = MethodReference.findOrCreate(T, mainMethod, Descriptor
								.findOrCreateUTF8(methodDescriptors[index++]));

						return new DefaultEntrypoint(mainRef, cha);
					}
				};
			}
		};
	}

	/////////////////////////////////////
	// make entrypoints from main methods

	public static Iterable<Entrypoint> makeMainEntrypoints(ClassLoaderReference clr, IClassHierarchy cha) {
		if (cha == null) {
			throw new IllegalArgumentException("cha is null");
		}
		final Atom mainMethod = Atom.findOrCreateAsciiAtom("method");
		final HashSet<Entrypoint> result = HashSetFactory.make();
		for (IClass klass : cha) {
			if (klass.getClassLoader().getReference().equals(clr)) {
				MethodReference mainRef = MethodReference.findOrCreate(klass.getReference(), mainMethod, Descriptor
						.findOrCreateUTF8("([Ljava/lang/String;)V"));
				IMethod m = klass.getMethod(mainRef.getSelector());
				if (m != null) {
					result.add(new DefaultEntrypoint(m, cha));
				}
			}
		}
		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				return result.iterator();
			}
		};
	}

	public static Iterable<Entrypoint> makeMainEntrypoints(final ClassLoaderReference loaderRef, final IClassHierarchy cha,
			final String[] classNames) throws IllegalArgumentException, IllegalArgumentException, IllegalArgumentException {

		if (classNames == null) {
			throw new IllegalArgumentException("classNames == null");
		}
		if (classNames.length == 0) {
			throw new IllegalArgumentException("classNames.length == 0");
		}
		if (classNames[0] == null && 0 < classNames.length) {
			throw new IllegalArgumentException("(0 < classNames.length) and (classNames[0] == null)");
		}

		for (int i = 0; i < classNames.length; i++) {
			if (classNames[i].indexOf("L") != 0) {
				throw new IllegalArgumentException("Expected class name to start with L " + classNames[i]);
			}
			if (classNames[i].indexOf(".") > 0) {
				Assertions.productionAssertion(false, "Expected class name formatted with /, not . " + classNames[i]);
			}
		}

		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				final Atom mainMethod = Atom.findOrCreateAsciiAtom("main");
				return new Iterator<Entrypoint>() {
					private int index = 0;

					public void remove() {
						Assertions.UNREACHABLE();
					}

					public boolean hasNext() {
						return index < classNames.length;
					}

					public Entrypoint next() {
						TypeReference T = TypeReference.findOrCreate(loaderRef, TypeName.string2TypeName(classNames[index++]));
						MethodReference mainRef = MethodReference.findOrCreate(T, mainMethod, Descriptor
								.findOrCreateUTF8("([Ljava/lang/String;)V"));
						return new DefaultEntrypoint(mainRef, cha);
					}
				};
			}
		};
	}

	public CallGraph getCallGraph() {
		return callGraph;
	}
}
