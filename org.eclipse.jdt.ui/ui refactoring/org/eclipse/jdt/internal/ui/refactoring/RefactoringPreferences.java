/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.ui.JavaPlugin;

public class RefactoringPreferences {

	public static final String PREF_ERROR_PAGE_SEVERITY_THRESHOLD= PreferenceConstants.REFACTOR_ERROR_PAGE_SEVERITY_THRESHOLD;
	public static final String PREF_SAVE_ALL_EDITORS= PreferenceConstants.REFACTOR_SAVE_ALL_EDITORS;
	
	public static int getCheckPassedSeverity() {
		String value= JavaPlugin.getDefault().getPreferenceStore().getString(PREF_ERROR_PAGE_SEVERITY_THRESHOLD);
		try {
			return Integer.valueOf(value).intValue() - 1;
		} catch (NumberFormatException e) {
			return RefactoringStatus.ERROR;
		}
	}
	
	public static boolean getSaveAllEditors() {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(PREF_SAVE_ALL_EDITORS);
	}
	
	public static void setSaveAllEditors(boolean save) {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(RefactoringPreferences.PREF_SAVE_ALL_EDITORS, save);
	}
}
