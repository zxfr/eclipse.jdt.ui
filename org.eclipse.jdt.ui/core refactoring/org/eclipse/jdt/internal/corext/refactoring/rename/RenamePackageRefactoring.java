/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.rename;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResult;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenamePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdatingRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IRenameRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdatingRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;


public class RenamePackageRefactoring extends Refactoring implements IRenameRefactoring, IReferenceUpdatingRefactoring, ITextUpdatingRefactoring{
	
	private IPackageFragment fPackage;
	private String fNewName;
	private SearchResultGroup[] fOccurrences;
	private TextChangeManager fChangeManager;
	private boolean fUpdateReferences;
	
	private boolean fUpdateJavaDoc;
	private boolean fUpdateComments;
	private boolean fUpdateStrings;
	
	
	public RenamePackageRefactoring(IPackageFragment pack){
		Assert.isNotNull(pack);
		fPackage= pack;
		fNewName= pack.getElementName();
		fUpdateReferences= true;
		fUpdateJavaDoc= false;
		fUpdateComments= false;
		fUpdateStrings= false;
	}
	
	/* non java-doc
	 * @see IRefactoring#getName
	 */
	public String getName(){
		return RefactoringCoreMessages.getFormattedString("RenamePackageRefactoring.name",  //$NON-NLS-1$
						new String[]{fPackage.getElementName(), fNewName});
	}
	
	public Object getNewElement(){
		IJavaElement parent= fPackage.getParent();
		if (!(parent instanceof IPackageFragmentRoot))
			return fPackage;//??
			
		IPackageFragmentRoot root= (IPackageFragmentRoot)parent;
		return root.getPackageFragment(fNewName);
	}
	
	/*
	 * @see ITextUpdatingRefactoring#canEnableTextUpdating()
	 */
	public boolean canEnableTextUpdating() {
		return true;
	}
	
	/*
	 * @see ITextUpdatingRefactoring#getUpdateJavaDoc()
	 */
	public boolean getUpdateJavaDoc() {
		return fUpdateJavaDoc;
	}

	/*
	 * @see ITextUpdatingRefactoring#getUpdateComments()
	 */
	public boolean getUpdateComments() {
		return fUpdateComments;
	}

	/*
	 * @see ITextUpdatingRefactoring#getUpdateStrings()
	 */
	public boolean getUpdateStrings() {
		return fUpdateStrings;
	}

	/*
	 * @see ITextUpdatingRefactoring#setUpdateJavaDoc(boolean)
	 */
	public void setUpdateJavaDoc(boolean update) {
		fUpdateJavaDoc= update;
	}

	/*
	 * @see ITextUpdatingRefactoring#setUpdateComments(boolean)
	 */
	public void setUpdateComments(boolean update) {
		fUpdateComments= update;
	}

	/*
	 * @see ITextUpdatingRefactoring#setUpdateStrings(boolean)
	 */
	public void setUpdateStrings(boolean update) {
		fUpdateStrings= update;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#setNewName
	 */	
	public final void setNewName(String newName){
		Assert.isNotNull(newName);
		fNewName= newName;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#getCurrentName
	 */
	public final String getCurrentName(){
		return fPackage.getElementName();
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#getNewName
	 */	
	public final String getNewName(){
		return fNewName;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#canUpdateReferences()
	 */
	public boolean canEnableUpdateReferences() {
		return true;
	}

	/* non java-doc
	 * @see IRenameRefactoring#setUpdateReferences(boolean)
	 */
	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}	
	
	/* non java-doc
	 * @see IRenameRefactoring#getUpdateReferences()
	 */	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}
	
	//--- preconditions
	
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException{
		pm.beginTask("", 1); //$NON-NLS-1$
		RefactoringStatus result= new RefactoringStatus();
		result.merge(Checks.checkAvailability(fPackage));
		
		if (fPackage.isDefaultPackage())
			result.addFatalError(""); //$NON-NLS-1$
		pm.done();	
		return result;
	}
	
	public RefactoringStatus checkNewName(String newName) throws JavaModelException{
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkPackageName(newName);
		if (Checks.isAlreadyNamed(fPackage, newName))
			result.addFatalError(RefactoringCoreMessages.getString("RenamePackageRefactoring.another_name")); //$NON-NLS-1$
		else if (fPackage.getElementName().equalsIgnoreCase(newName))	
			result.addFatalError(RefactoringCoreMessages.getString("RenamePackageRefactoring.different_case")); //$NON-NLS-1$
		result.merge(checkPackageInCurrentRoot(newName));
		return result;
	}
	
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException{
		try{
			pm.beginTask("", 14); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.getString("RenamePackageRefactoring.checking")); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkNewName(fNewName));
			pm.worked(1);
			result.merge(checkForNativeMethods());
			pm.worked(1);
			result.merge(checkForMainMethods());
			pm.worked(1);
			
			if (fPackage.isReadOnly()){
				String message= RefactoringCoreMessages.getFormattedString("RenamePackageRefactoring.Packagered_only",  //$NON-NLS-1$
									fPackage.getElementName()); 
				result.addWarning(message);
			}	
				
			if (fPackage.getResource().isReadOnly()){
				String message= RefactoringCoreMessages.getFormattedString("RenamePackageRefactoring.resource_read_only", //$NON-NLS-1$
									fPackage.getElementName());
				result.addWarning(message);
			}	
				
			if (!fUpdateReferences)
				return result;
			
			pm.setTaskName(RefactoringCoreMessages.getString("RenamePackageRefactoring.searching"));	 //$NON-NLS-1$
			fOccurrences= getReferences(new SubProgressMonitor(pm, 6));	
			pm.setTaskName(RefactoringCoreMessages.getString("RenamePackageRefactoring.checking")); //$NON-NLS-1$
			result.merge(analyzeAffectedCompilationUnits());
			result.merge(checkPackageName(fNewName));
			pm.worked(1);
			if (result.hasFatalError())
				return result;
			
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 3));
			result.merge(validateModifiesFiles());
			return result;
		} catch (CoreException e){	
			throw new JavaModelException(e);
		} finally{
			pm.done();
		}	
	}
	
	private IJavaSearchScope createRefactoringScope()  throws JavaModelException{
		return RefactoringScopeFactory.create(fPackage);
	}
	
	private ISearchPattern createSearchPattern(){
		return SearchEngine.createSearchPattern(fPackage, IJavaSearchConstants.REFERENCES);
	}
	
	private SearchResultGroup[] getReferences(IProgressMonitor pm) throws JavaModelException{
		return RefactoringSearchEngine.search(pm, createRefactoringScope(), createSearchPattern());
	}
		
	private RefactoringStatus checkForMainMethods() throws JavaModelException{
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < cus.length; i++)
			result.merge(Checks.checkForMainMethods(cus[i]));
		return result;
	}
	
	private RefactoringStatus checkForNativeMethods() throws JavaModelException{
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < cus.length; i++)
			result.merge(Checks.checkForNativeMethods(cus[i]));
		return result;
	}
	
	/*
	 * returns true if the new name is ok if the specified root.
	 * if a package fragment with this name exists and has java resources,
	 * then the name is not ok.
	 */
	public static boolean isPackageNameOkInRoot(String newName, IPackageFragmentRoot root) throws JavaModelException{
		IPackageFragment pack= root.getPackageFragment(newName);
		if (! pack.exists())
			return true;
		else if (! pack.hasSubpackages()) //leaves are no good
			return false;			
		else if (pack.containsJavaResources())
			return false;
		else if (pack.getNonJavaResources().length != 0)
			return false;
		else 
			return true;	
	}
	
	private RefactoringStatus checkPackageInCurrentRoot(String newName) throws JavaModelException{
		if (isPackageNameOkInRoot(newName, getPackageFragmentRoot()))
			return null;
		else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("RenamePackageRefactoring.package_exists"));//$NON-NLS-1$
	}

	private IPackageFragmentRoot getPackageFragmentRoot() {
		return ((IPackageFragmentRoot)fPackage.getParent());
	}
	
	private RefactoringStatus checkPackageName(String newName) throws JavaModelException{		
		IPackageFragmentRoot[] roots= fPackage.getJavaProject().getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			if (! isPackageNameOkInRoot(newName, roots[i]))
				return RefactoringStatus.createErrorStatus(RefactoringCoreMessages.getFormattedString("RenamePackageRefactoring.aleady_exists", fNewName));//$NON-NLS-1$
		}
		return new RefactoringStatus();
	}
		
	private RefactoringStatus analyzeAffectedCompilationUnits() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		fOccurrences= Checks.excludeCompilationUnits(fOccurrences, result);
		if (result.hasFatalError())
			return result;
		
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fOccurrences));	
		return result;
	}

	private IFile[] getAllCusInPackageAsFiles() throws JavaModelException{
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		List files= new ArrayList(cus.length);
		for (int i= 0; i < cus.length; i++) {
            IResource res= ResourceUtil.getResource(cus[i]);
            if (res != null && res.getType() == IResource.FILE)
            	files.add(res);
        }
		return (IFile[]) files.toArray(new IFile[files.size()]);
	}
	
	private IFile[] getAllFilesToModify() throws CoreException{
		//cannot use Arrays.asList to create this temp - addAll is not supported on list created by Arrays.asList
		List combined= new ArrayList();
		combined.addAll(Arrays.asList(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits())));
		combined.addAll(Arrays.asList(getAllCusInPackageAsFiles()));
		return (IFile[]) combined.toArray(new IFile[combined.size()]);
	}
	
	private RefactoringStatus validateModifiesFiles() throws CoreException{
		return Checks.validateModifiesFiles(getAllFilesToModify());
	}
	
	// ----------- Changes ---------------
	
	public IChange createChange(IProgressMonitor pm) throws JavaModelException{
		try{
			pm.beginTask(RefactoringCoreMessages.getString("RenamePackageRefactoring.creating_change"), 1); //$NON-NLS-1$
			CompositeChange builder= new CompositeChange();
	
			if (fUpdateReferences)
				builder.addAll(fChangeManager.getAllChanges());
				
			builder.add(new RenamePackageChange(fPackage, fNewName));
			pm.worked(1);
			return builder;
		} catch (CoreException e){
			throw new JavaModelException(e);
		} finally{
			pm.done();
		}	
	}
	
	private void addTextMatches(TextChangeManager manager, IProgressMonitor pm) throws JavaModelException{
		TextMatchFinder.findTextMatches(pm, createRefactoringScope(), this, manager);
	}
	
	private TextEdit createTextChange(SearchResult searchResult) {
		return SimpleTextEdit.createReplace(searchResult.getStart(), searchResult.getEnd() - searchResult.getStart(), fNewName);
	}
	
	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException{
		pm.beginTask("", 2); //$NON-NLS-1$
		TextChangeManager manager= new TextChangeManager();
		
		pm.subTask(RefactoringCoreMessages.getString("RenamePackageRefactoring.searching_text")); //$NON-NLS-1$
		addTextMatches(manager, new SubProgressMonitor(pm, 1));
		
		addReferenceUpdates(manager, new SubProgressMonitor(pm, 1));
		
		return manager;
	}
	
	private void addOccurrences(IProgressMonitor pm, CompositeChange builder) throws CoreException{
		pm.beginTask("", 2); //$NON-NLS-1$
		TextChangeManager manager= new TextChangeManager();
		
		pm.subTask(RefactoringCoreMessages.getString("RenamePackageRefactoring.searching_text")); //$NON-NLS-1$
		addTextMatches(manager, new SubProgressMonitor(pm, 1));
		
		addReferenceUpdates(manager, new SubProgressMonitor(pm, 1));
		
		//putting it together
		builder.addAll(manager.getAllChanges());
	}
	
	private void addReferenceUpdates(TextChangeManager manager, IProgressMonitor pm) throws CoreException{
		pm.beginTask("", fOccurrences.length); //$NON-NLS-1$
		for (int i= 0; i < fOccurrences.length; i++){
			ICompilationUnit cu= fOccurrences[i].getCompilationUnit();
			if (cu == null)
				continue;
			String name= RefactoringCoreMessages.getString("RenamePackageRefactoring.update_reference"); //$NON-NLS-1$
			ICompilationUnit wc= 	WorkingCopyUtil.getWorkingCopyIfExists(cu);
			SearchResult[] results= fOccurrences[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				manager.get(wc).addTextEdit(name, createTextChange(results[j]));
			}
			pm.worked(1);
		}	
	}
}