/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.util;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.codemanipulation.StubUtility;

/**
 * Utility methods for the Java Model. These methods should be part of
 * <code>IJavaModel</code> but unfortunately they aren't.
 */
public class JavaModelUtility {

	/** 
	 * Finds a type by its qualified type name. (dot separated)
	 * @param jproject the java project to search in
	 * @param str the fully qualified name (type name with enclosing type names and package (all separated by dots))
	 * @return the type found, or null if not existing
	 * The method does not find inner types. Waiting for a Java Core solution
	 */	
	public static IType findType(IJavaProject jproject, String fullyQualifiedName) throws JavaModelException {
		String pathStr= fullyQualifiedName.replace('.', '/') + ".java"; //$NON-NLS-1$
		IJavaElement jelement= jproject.findElement(new Path(pathStr));
		if (jelement instanceof ICompilationUnit) {
			String simpleName= Signature.getSimpleName(fullyQualifiedName);
			return ((ICompilationUnit)jelement).getType(simpleName);
		} else if (jelement instanceof IClassFile) {
			return ((IClassFile)jelement).getType();
		}
		return null;
	}

	/** 
	 * Finds a type by package and type name.
	 * @param jproject the java project to search in
	 * @param pack The package name
	 * @param typeQualifiedName the type qualified name (type name with enclosing type names (separated by dots))
	 * @return the type found, or null if not existing
	 */	
	public static IType findType(IJavaProject jproject, String pack, String typeQualifiedName) throws JavaModelException {
		// should be supplied from java core
		int dot= typeQualifiedName.indexOf('.');
		if (dot == -1) {
			return findType(jproject, concatenateName(pack, typeQualifiedName));
		}
		IPath packPath;
		if (pack.length() > 0) {
			packPath= new Path(pack.replace('.', '/'));
		} else {
			packPath= new Path(""); //$NON-NLS-1$
		}
		// fixed for 1GEXEI6: ITPJUI:ALL - Incorrect error message on class creation wizard
		IPath path= packPath.append(typeQualifiedName.substring(0, dot) + ".java"); //$NON-NLS-1$
		IJavaElement elem= jproject.findElement(path);
		if (elem instanceof ICompilationUnit) {
			return findTypeInCompilationUnit((ICompilationUnit)elem, typeQualifiedName);
		} else if (elem instanceof IClassFile) {
			path= packPath.append(typeQualifiedName.replace('.', '$') + ".class"); //$NON-NLS-1$
			elem= jproject.findElement(path);
			if (elem instanceof IClassFile) {
				return ((IClassFile)elem).getType();
			}
		}
		return null;
	}
	
	/** 
	 * Finds a type in a compilation unit.
	 * @param cu the compilation unit to search in
	 * @param typeQualifiedName the type qualified name (type name with enclosing type names (separated by dots))
	 * @return the type found, or null if not existing
	 */		
	public static IType findTypeInCompilationUnit(ICompilationUnit cu, String typeQualifiedName) throws JavaModelException {
		IType[] types= cu.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			String currName= getTypeQualifiedName(types[i]);
			if (typeQualifiedName.equals(currName)) {
				return types[i];
			}
		}
		return null;
	}
	
	/** 
	 * Finds a a member in a compilation unit. Typical usage is to find the corresponding
	 * member in its working copy.
	 * @param cu the compilation unit (eg. working copy) to search in
	 * @param member the member (eg. from the original)
	 * @return the member found, or null if not existing
	 */		
	public static IMember findMemberInCompilationUnit(ICompilationUnit cu, IMember member) throws JavaModelException {
		if (member.getElementType() == IJavaElement.TYPE) {
			return findTypeInCompilationUnit(cu, getTypeQualifiedName((IType)member));
		} else {
			IType declaringType= findTypeInCompilationUnit(cu, getTypeQualifiedName(member.getDeclaringType()));
			if (declaringType != null) {
				IMember result= null;
				switch (member.getElementType()) {
				case IJavaElement.FIELD:
					result= declaringType.getField(member.getElementName());
					break;
				case IJavaElement.METHOD:
					result= StubUtility.findMethod((IMethod)member, declaringType);
					break;
				case IJavaElement.INITIALIZER:
					result= declaringType.getInitializer(0);
					break;					
				}
				if (result != null && result.exists()) {
					return result;
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns the qualified type name of the given type using '.' as separators.
	 * This is a replace for IType.getTypeQualifiedName()
	 * which uses '$' as separators. As '$' is also a valid character in an id
	 * this is ambiguous. Hoping for a fix in JavaCore (1GCFUNT)
	 */
	public static String getTypeQualifiedName(IType type) {
		StringBuffer buf= new StringBuffer();
		getTypeQualifiedName(type, buf);
		return buf.toString();
	}
	
	private static void getTypeQualifiedName(IType type, StringBuffer buf) {
		IType outerType= type.getDeclaringType();
		if (outerType != null) {
			getTypeQualifiedName(outerType, buf);
			buf.append('.');
		}
		buf.append(type.getElementName());
	}	

	/**
	 * Returns the fully qualified name of the given type using '.' as separators.
	 * This is a replace for IType.getFullyQualifiedTypeName
	 * which uses '$' as separators. As '$' is also a valid character in an id
	 * this is ambiguous. Hoping for a fix in JavaCore (1GCFUNT)
	 */
	public static String getFullyQualifiedName(IType type) {
		StringBuffer buf= new StringBuffer();
		String packName= type.getPackageFragment().getElementName();
		if (packName.length() > 0) {
			buf.append(packName);
			buf.append('.');
		}
		getTypeQualifiedName(type, buf);
		return buf.toString();
	}
	
	/**
	 * Returns the fully qualified name of a type's container. (package name + enclosing type name)
	 */
	public static String getTypeContainerName(IType type) {
		IType outerType= type.getDeclaringType();
		if (outerType != null) {
			return getFullyQualifiedName(outerType);
		} else {
			return type.getPackageFragment().getElementName();
		}
	}
		
	/**
	 * Returns the raw class path entry corresponding to a package fragment root
	 * or null if there isn't a corresponding entry.
	 */
	public static IClasspathEntry getRawClasspathEntry(IPackageFragmentRoot root) throws JavaModelException {
		IPath path= root.getPath();
		IClasspathEntry[] entries= root.getJavaProject().getRawClasspath();
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
		
			if (curr.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				curr= JavaCore.getResolvedClasspathEntry(curr);
			}
			if (curr != null && curr.getEntryKind() == IClasspathEntry.CPE_LIBRARY && path.equals(curr.getPath())) {
				return entries[i];
			}
		}
		return null;
	}

	/**
	 * Concatenates to names. Uses a dot for separation
	 * Both strings can be empty or null
	 */
	public static String concatenateName(String name1, String name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length() > 0) {
			buf.append(name1);
			if (name2 != null && name2.length() > 0) {
				buf.append('.');
				buf.append(name2);
			}
		}
		return buf.toString();
	}
	
	/**
	 * Evaluate if a package is visible from another package with the given modifiers
	 */
	public static boolean isVisible(IPackageFragment ourpack, int otherflags, IPackageFragment otherpack) {
		if (Flags.isPublic(otherflags) || Flags.isProtected(otherflags)) {
			return true;
		}
		if (Flags.isPrivate(otherflags)) {
			return false;
		}		
		
		return (ourpack != null && ourpack.equals(otherpack));
	}
	
	
	// --------- project dependencies -----------
		
	public static void updateRequiredProjects(IJavaProject jproject, String[] prevRequiredProjects, IProgressMonitor monitor) throws CoreException {
		String[] newRequiredProjects= jproject.getRequiredProjectNames();

		ArrayList prevEntries= new ArrayList(Arrays.asList(prevRequiredProjects));
		ArrayList newEntries= new ArrayList(Arrays.asList(newRequiredProjects));
		
		IProject proj= jproject.getProject();
		IProjectDescription projDesc= proj.getDescription();  
		
		ArrayList newRefs= new ArrayList();
		IProject[] referencedProjects= projDesc.getReferencedProjects();
		for (int i= 0; i < referencedProjects.length; i++) {
			String curr= referencedProjects[i].getName();
			if (newEntries.remove(curr) || !prevEntries.contains(curr)) {
				newRefs.add(referencedProjects[i]);
			}
		}
		IWorkspaceRoot root= proj.getWorkspace().getRoot();
		for (int i= 0; i < newEntries.size(); i++) {
			String curr= (String) newEntries.get(i);
			newRefs.add(root.getProject(curr));
		}		
		projDesc.setReferencedProjects((IProject[]) newRefs.toArray(new IProject[newRefs.size()]));
		proj.setDescription(projDesc, monitor);
	}	
	
	/**
	 * Convert an import declaration into the java element the import declaration
	 * stands for. An on demand import declaration is converted into a package
	 * fragement or type depending of the kind of import statement (e.g. p1.p2.*
	 * versus p1.p2.T1.*). A normal import declaration is converted into the 
	 * corresponding <code>IType</code>.
	 */
	public static IJavaElement convertFromImportDeclaration(IImportDeclaration declaration) throws JavaModelException {
		if (declaration.isOnDemand()) {
			String pattern= declaration.getElementName();
			pattern= pattern.substring(0, pattern.length() - 2);
			IJavaProject project= declaration.getJavaProject();
			
			// XXX: 1GBRLSV: ITPJCORE:WIN2000 - Question: how to I find an inner type
			// First try if the import statement is of form p1.p2.T1.* which would lead
			// to a type not to a package.
			IJavaElement result= findType(project, pattern);
			if (result != null)
				return result;
			
			return convertToPackageFragment(pattern, project);
		} else {
			return convertToType(declaration);	
		}
	}
	
	private static IPackageFragment convertToPackageFragment(IImportDeclaration declaration) {
		String pattern= declaration.getElementName();
		pattern= pattern.substring(0, pattern.length() - 2);
		return convertToPackageFragment(pattern, declaration.getJavaProject());
	}	
		
	private static IPackageFragment convertToPackageFragment(String pattern, IJavaProject project) {
		
		try {
			// Check the project itself.
			// XXX: 1GAOLWQ: ITPJCORE:WIN2000 - IJavaProject.findPackageFragment strange semantic
			IPackageFragment[] packages= project.getPackageFragments();
			for (int i= 0; i < packages.length; i++) {
				if (pattern.equals(packages[i].getElementName()))
					return packages[i];
			}
			
			// Convert to a path and search on the class path.
			pattern= pattern.replace('.', IPath.SEPARATOR);
			IPath path= new Path(pattern).makeAbsolute();
			return project.findPackageFragment(path);
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
			return null;
		}	
	}
	
	private static IType convertToType(IImportDeclaration declaration) throws JavaModelException {
		IJavaProject project= declaration.getJavaProject();
		return findType(project, declaration.getElementName());
	}
	
	/**
	 * Returns true if the element is on the build path of the given project
	 */	
	public static boolean isOnBuildPath(IJavaElement element, IJavaProject jproject) throws JavaModelException {
		IPath rootPath;
		if (element.getElementType() == IJavaElement.JAVA_PROJECT) {
			rootPath= ((IJavaProject)element).getProject().getFullPath();
		} else {
			IPackageFragmentRoot root= getPackageFragmentRoot(element);
			if (root == null) {
				return false;
			}
			rootPath= root.getPath();
		}
		return jproject.findPackageFragmentRoot(rootPath) != null;
	}
	
	/**
	 * Returns true if the element is on the build path of its project
	 * Not correct in general.
	 */	
	public static boolean isOnBuildPath(IJavaElement element) throws JavaModelException {
		return isOnBuildPath(element, element.getJavaProject());
	}
	
	/**
	 * Returns the package fragment root of <code>IJavaElement</code>. If the given
	 * element is already a package fragment root, the element itself is returned.
	 */
	public static IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		return (IPackageFragmentRoot)findElementOfKind(element, IJavaElement.PACKAGE_FRAGMENT_ROOT);
	}

	/**
	 * Returns the parent of the supplied java element that conforms to the given 
	 * parent type or <code>null</code>, if such a parent doesn't exit.
	 */
	public static IJavaElement getParent(IJavaElement element, int kind) {
		if (element == null)
			return null;
		return findElementOfKind(element.getParent(), kind);	
	}
	
	/**
	 * Returns the first openable parent. If the given element is openable, the element
	 * itself is returned.
	 */
	public static IOpenable getOpenable(IJavaElement element) {
		while (element != null && !(element instanceof IOpenable)) {
			element= element.getParent();
		}
		return (IOpenable)element;	
	}	
	
	/**
	 * Returns the first java element that conforms to the given type walking the
	 * java element's parent relationship. If the given element alrady conforms to
	 * the given kind, the element is returned.
	 */
	public static IJavaElement findElementOfKind(IJavaElement element, int kind) {
		while (element != null && element.getElementType() != kind)
			element= element.getParent();
		return element;				
	}
	
	private static final String SIG1= Signature.createArraySignature(Signature.createTypeSignature("String", false), 1); //$NON-NLS-1$
	private static final String SIG2= Signature.createArraySignature(Signature.createTypeSignature("java.lang.String", false), 1); //$NON-NLS-1$
	private static final String SIG3= Signature.createArraySignature(Signature.createTypeSignature("java.lang.String", true), 1); //$NON-NLS-1$

	/**
	 * Checks whether the given IType has a main method or not.
	 */
	public static boolean hasMainMethod(IType type) {
		if (isStaticPublicVoidMethod(type.getMethod("main", new String[] { SIG1 })) ||  //$NON-NLS-1$
			isStaticPublicVoidMethod(type.getMethod("main", new String[] { SIG2 })) ||  //$NON-NLS-1$
			isStaticPublicVoidMethod(type.getMethod("main", new String[] { SIG3 }))) { //$NON-NLS-1$
				return true;
		}
		
		return false;
	}
	
	public static boolean isMainMethod(IMethod method) {
		try {			
			if (!isStaticPublicVoidMethod(method))
				return false;
			String signature= method.getSignature();
			if ("([Qjava.lang.String;)V".equals(signature) || //$NON-NLS-1$
				"([Ljava/lang/String;)V".equals(signature)) //$NON-NLS-1$
				return true;
			if ("([QString;)V".equals(signature)) { //$NON-NLS-1$
				String[][] resolvedNames= method.getDeclaringType().resolveType("String"); //$NON-NLS-1$
				if (resolvedNames != null && resolvedNames.length > 0 
					&& "java.lang".equals(resolvedNames[0][0]) && "String".equals(resolvedNames[0][1])) //$NON-NLS-1$ //$NON-NLS-2$
					return true;
			}
			return false;
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		return false;
	}
	
	private static boolean isStaticPublicVoidMethod(IMethod m) {
		try {
			return m.exists() && "V".equals(m.getReturnType()) && Flags.isStatic(m.getFlags()) && Flags.isPublic(m.getFlags()); //$NON-NLS-1$
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
			return false;
		}
	}
	
	
}