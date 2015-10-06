package com.beautysunflower.plugin.eclipse.native2ascii.builder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.beautysunflower.plugin.eclipse.native2ascii.Activator;

public class PropertiesBuilder extends IncrementalProjectBuilder {

	ILog log = Activator.getDefault().getLog();

	class SampleDeltaVisitor implements IResourceDeltaVisitor {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" DeltaVisitor...ADDED", null));
				native2ascii(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" DeltaVisitor...REMOVED", null));
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" DeltaVisitor...CHANGED", null));
				native2ascii(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class SampleResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			try {
				log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" ResourceVisitor...", null));
				native2ascii(resource);
			} catch (CoreException e) {
				// TODO Auto-generated catch block
			}
			//return true to continue visiting children.
			return true;
		}
	}

	public static final String BUILDER_ID = "com.beautysunflower.plugin.eclipse.native2ascii.propertiesBuilder";

	private static final String PROPERTIES = ".properties";
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new SampleResourceVisitor());
		} catch (CoreException e) {
		}
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new SampleDeltaVisitor());
	}
	
	private void native2ascii(IResource resource) throws CoreException {
		log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" 开始转码...", null));
		if (!resource.getName().endsWith(PROPERTIES)) {
			log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" 不是以"+PROPERTIES+"结尾，直接返回。", null));
			return;
		}
		IJavaElement srcFolder = JavaCore.create(resource.getParent());
		if (srcFolder == null) {
			log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getParent().toString()+" 无法找到源文件路径，直接返回。", null));
			return;
		}
		if (srcFolder.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
			srcFolder = srcFolder.getParent();
		}
		if (srcFolder.getElementType() != IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, srcFolder.getElementType()+" 源码所在元素类型不正确，直接返回。", null));
			return;
		}
		IJavaProject jproject = JavaCore.create(resource.getProject());
		IPath outputLocation = jproject.getOutputLocation();
		IFile file = (IFile) resource;
		StringBuffer buff = new StringBuffer();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					file.getContents(), file.getCharset()));
			String line = null;
			while ((line = in.readLine()) != null) {
				buff.append(native2Ascii(line)).append('\n');
			}
			in.close();
		} catch (IOException e) {
			IWorkbench workbench = PlatformUI.getWorkbench();
			IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
			IStatus status = new Status(IStatus.ERROR, Activator.getDefault()
					.getBundle().getSymbolicName(),
					"Exception thown while reading resource:" + file.getName(),
					e);
			ErrorDialog
					.openError(
							window.getShell(),
							"Error read native2ascii setting",
							"Exception thrown while reading native2ascii builder setting",
							status);
			log.log(status);
		}
		ByteArrayInputStream bin = new ByteArrayInputStream(buff.toString()
				.getBytes());
		IPath path = file.getProjectRelativePath()
				.removeFirstSegments(
						srcFolder.getResource().getProjectRelativePath()
								.segmentCount());
		IPath destFilePath = outputLocation.append(path);
		IFolder folder = jproject.getProject().getFolder(
				destFilePath.removeFirstSegments(1).removeLastSegments(1));
		IFile destFile = folder.getFile(destFilePath.lastSegment());
		if (destFile.exists()) {
			destFile.setContents(bin, false, false, null);
		} else {
			destFile.create(bin, false, null);
		}
		log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, 0, resource.getName()+" 转码结束.", null));
	}

	private static String native2Ascii(String str) {
		StringBuffer sb = new StringBuffer(str.length());
		sb.setLength(0);
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			sb.append(native2Ascii(c));
		}
		return (new String(sb));
	}

	private static StringBuffer native2Ascii(char charater) {
		StringBuffer sb = new StringBuffer();
		if (charater > 255) {
			sb.append("\\u");
			int lowByte = (charater >>> 8);
			sb.append(int2HexString(lowByte));
			int highByte = (charater & 0xFF);
			sb.append(int2HexString(highByte));
		} else {
			sb.append(charater);
		}
		return sb;
	}

	private static String int2HexString(int code) {
		String hexString = Integer.toHexString(code);
		if (hexString.length() == 1)
			hexString = "0" + hexString;
		return hexString;
	}
}
