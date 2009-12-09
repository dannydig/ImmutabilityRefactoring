package edu.uiuc.immutability.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.io.FileProvider;

import edu.uiuc.immutability.Activator;

public class WALAwithConfigurationFileAnalysis extends WALAAnalysis {

	private static final String WALA_CONFIG_FILE = "wala.config.txt";

	private final IJavaProject project;

	private File configFile;


	public WALAwithConfigurationFileAnalysis(IJavaProject project,
			IProgressMonitor monitor) throws IOException, ClassHierarchyException, JavaModelException, IllegalArgumentException, CallGraphBuilderCancelException {
		this.project = project;
		this.monitor = monitor;

		// generate scope file
		String scopeFile = generateScopeFile();
		String exclusionsFile = FileProvider.getFileFromPlugin(Activator.getDefault(), "Java60RegressionExclusions.txt").getAbsolutePath();

		scope = CallGraphTestUtil.makeJ2SEAnalysisScope(scopeFile, exclusionsFile);
		cha = ClassHierarchy.make(scope);

		System.out.println(scope);
	}

	private String generateScopeFile() throws JavaModelException, IOException {
		IPath binaryDir = project.getOutputLocation();
		IPath projectPath = project.getPath();

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IPath workspaceLocation = root.getLocation();

		String configFilePath = workspaceLocation.toOSString()
			+ projectPath.toOSString() + File.separator + WALA_CONFIG_FILE;
		
		configFile = new File(configFilePath);

		String binaryDirPath = workspaceLocation.toOSString() + binaryDir.toOSString();

		BufferedWriter bw = new BufferedWriter(new FileWriter(configFile));
		IPath jreLibPath = JavaCore.getClasspathVariable("JRE_LIB");
		String jreLib = jreLibPath.toPortableString();
		System.out.println();
		
		bw.write("Primordial,Java,jarFile," + jreLib + "\n");
		addLibraryDependences(bw, workspaceLocation);
		bw.write("Primordial,Java,jarFile,primordial.jar.model\n");
		bw.write("Application,Java,binaryDir," + binaryDirPath + "\n");

		bw.close();

		return configFilePath;
	}

	private void addLibraryDependences(BufferedWriter bw,
			IPath workspaceLocation) throws JavaModelException, IOException {
		IClasspathEntry[] rawClasspath = project.getRawClasspath();
		for (int i = 0; i < rawClasspath.length; i++) {
			IClasspathEntry iClasspathEntry = rawClasspath[i];
			if ((iClasspathEntry.getEntryKind() == iClasspathEntry.CPE_LIBRARY) && (iClasspathEntry.getContentKind() == IPackageFragmentRoot.K_BINARY)) {
				IPath fullLibraryPath = workspaceLocation.append(iClasspathEntry.getPath());
				String libPath = fullLibraryPath.toPortableString();
				if (!libPath.endsWith("testutil.jar"))
					bw.write("Primordial,Java,jarFile," + libPath + "\n");
				else
					// We need to differentiate between testutil.jar,
					// which isn't in the project path, and other libs in the build path
					bw.write("Primordial,Java,jarFile," + iClasspathEntry.getPath().toPortableString() + "\n");
			}
		}
	}

	public void cleanup() {
		configFile.delete();
	}
}
