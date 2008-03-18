package org.python.pydev.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry;
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.python.pydev.core.ICallback;
import org.python.pydev.core.IInterpreterManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.REF;
import org.python.pydev.core.Tuple;
import org.python.pydev.core.bundle.BundleInfo;
import org.python.pydev.core.bundle.IBundleInfo;
import org.python.pydev.core.bundle.ImageCache;
import org.python.pydev.core.log.Log;
import org.python.pydev.dltk.console.ui.ScriptConsoleUIConstants;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.codecompletion.revisited.PythonPathHelper;
import org.python.pydev.editor.codecompletion.shell.AbstractShell;
import org.python.pydev.editor.templates.PyContextType;
import org.python.pydev.editorinput.PydevFileEditorInput;
import org.python.pydev.editorinput.PydevZipFileEditorInput;
import org.python.pydev.editorinput.PydevZipFileStorage;
import org.python.pydev.plugin.nature.PythonNature;
import org.python.pydev.plugin.nature.SystemPythonNature;
import org.python.pydev.pyunit.ITestRunListener;
import org.python.pydev.pyunit.PyUnitTestRunner;
import org.python.pydev.ui.filetypes.FileTypesPreferencesPage;
import org.python.pydev.ui.interpreters.JythonInterpreterManager;
import org.python.pydev.ui.interpreters.PythonInterpreterManager;


/**
 * The main plugin class - initialized on startup - has resource bundle for internationalization - has preferences
 */
public class PydevPlugin extends AbstractUIPlugin implements Preferences.IPropertyChangeListener {
	
    public static final String version = "REPLACE_VERSION";
    
    // ----------------- SINGLETON THINGS -----------------------------
    public static IBundleInfo info;
    public static IBundleInfo getBundleInfo(){
        if(PydevPlugin.info == null){
            PydevPlugin.info = new BundleInfo(PydevPlugin.getDefault().getBundle());
        }
        return PydevPlugin.info;
    }
    public static void setBundleInfo(IBundleInfo b){
        PydevPlugin.info = b;
    }
    // ----------------- END BUNDLE INFO THINGS --------------------------
	
    private static IInterpreterManager pythonInterpreterManager;
    public static void setPythonInterpreterManager(IInterpreterManager interpreterManager) {
        PydevPlugin.pythonInterpreterManager = interpreterManager;
    }
    public static IInterpreterManager getPythonInterpreterManager() {
        return getPythonInterpreterManager(false);
    }
    public static IInterpreterManager getPythonInterpreterManager(boolean haltOnStub) {
    	return pythonInterpreterManager;
    }

    
    
    
    private static IInterpreterManager jythonInterpreterManager;
    public static void setJythonInterpreterManager(IInterpreterManager interpreterManager) {
        PydevPlugin.jythonInterpreterManager = interpreterManager;
    }
    public static IInterpreterManager getJythonInterpreterManager() {
    	return getJythonInterpreterManager(false);
    }
    public static IInterpreterManager getJythonInterpreterManager(boolean haltOnStub) {
        return jythonInterpreterManager;
    }
    // ----------------- END SINGLETON THINGS --------------------------

    /**
     * returns the interpreter manager for a given nature
     * @param nature the nature from where we want to get the associated interpreter manager
     * 
     * @return the interpreter manager
     */
    public static IInterpreterManager getInterpreterManager(IPythonNature nature) {
        try {
            if (nature.isJython()) {
                return jythonInterpreterManager;
            } else if (nature.isPython()) {
                return pythonInterpreterManager;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Unable to get the interpreter manager for the nature passed.");
    }
    
    
    private static PydevPlugin plugin; //The shared instance.

    private ResourceBundle resourceBundle; //Resource bundle.

    /** The template store. */
    private TemplateStore fStore;

    /** The context type registry. */
    private ContributionContextTypeRegistry fRegistry = null;

    /** Key to store custom templates. */
    private static final String CUSTOM_TEMPLATES_PY_KEY = "org.python.pydev.editor.templates.PyTemplatePreferencesPage";

    public static final String DEFAULT_PYDEV_SCOPE = "org.python.pydev";


    /**
     * The constructor.
     */
    public PydevPlugin() {
        super();
        plugin = this;
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
        try {
            resourceBundle = ResourceBundle.getBundle("org.python.pydev.PyDevPluginResources");
        } catch (MissingResourceException x) {
            resourceBundle = null;
        }
        final Preferences preferences = plugin.getPluginPreferences();
        preferences.addPropertyChangeListener(this);
        
        //set them temporarily
        //setPythonInterpreterManager(new StubInterpreterManager(true));
        //setJythonInterpreterManager(new StubInterpreterManager(false));
        
        //changed: the interpreter manager is always set in the initialization (initialization 
        //has some problems if that's not done).
        setPythonInterpreterManager(new PythonInterpreterManager(preferences));
        setJythonInterpreterManager(new JythonInterpreterManager(preferences));

        //restore the nature for all python projects
        new Job("PyDev: Restoring projects python nature"){

            protected IStatus run(IProgressMonitor monitor) {
            	try{
	            	
	                IProject[] projects = getWorkspace().getRoot().getProjects();
	                for (int i = 0; i < projects.length; i++) {
	                    IProject project = projects[i];
	                    try {
	                        if (project.isOpen() && project.hasNature(PythonNature.PYTHON_NATURE_ID)) {
	                            PythonNature.addNature(project, monitor, null, null);
	                        }
	                    } catch (Exception e) {
	                        PydevPlugin.log(e);
	                    }
	                }
            	}catch(Throwable t){
            		t.printStackTrace();
            	}
                return Status.OK_STATUS;
            }
            
        }.schedule();
        
    }
    
    public static boolean isPythonInterpreterInitialized() {
    	return true;
	}
    
    public static boolean isJythonInterpreterInitialized() {
    	return true;
    }
    

    /**
     * This is called when the plugin is being stopped.
     * 
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception {
        
        try {
            //stop the running shells
            AbstractShell.shutdownAllShells();

            Preferences preferences = plugin.getPluginPreferences();
            preferences.removePropertyChangeListener(this);
            
            //save the natures (code completion stuff).
            IProject[] projects = getWorkspace().getRoot().getProjects();
            for (int i = 0; i < projects.length; i++) {
                try {
                    IProject project = projects[i];
                    if (project.isOpen()){
	                    IProjectNature n = project.getNature(PythonNature.PYTHON_NATURE_ID);
	                    if(n instanceof PythonNature){
	                        PythonNature nature = (PythonNature) n;
	                        nature.saveAstManager();
	                    }
                    }
                } catch (CoreException e) {
                    PydevPlugin.log(e);
                }
            }

        } finally{
	        super.stop(context);
        }
    }

    public static PydevPlugin getDefault() {
        return plugin;
    }

    public static String getPluginID() {
        return PydevPlugin.getBundleInfo().getPluginID();
    }

    /**
     * Returns the workspace instance.
     */
    public static IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    public static Status makeStatus(int errorLevel, String message, Throwable e) {
        return new Status(errorLevel, getPluginID(), errorLevel, message, e);
    }

    /**
     * Returns the string from the plugin's resource bundle, or 'key' if not found.
     */
    public static String getResourceString(String key) {
        ResourceBundle bundle = plugin.getResourceBundle();
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
    
    
    public void propertyChange(Preferences.PropertyChangeEvent event) {
        //		System.out.println( event.getProperty()
        //		 + "\n\told setting: "
        //		 + event.getOldValue()
        //		 + "\n\tnew setting: "
        //		 + event.getNewValue());
    }

    public static void log(String message, Throwable e) {
        log(IStatus.ERROR, message, e);
    }
    
    public static void log(int errorLevel, String message, Throwable e) {
        log(errorLevel, message, e, true);
    }
    public static void log(String message, Throwable e, boolean printToConsole) {
        log(IStatus.ERROR, message, e, printToConsole);
    }

    public static void logInfo(Exception e) {
        log(IStatus.INFO, e.getMessage(), e, true);
	}

    /**
     * @param errorLevel IStatus.[OK|INFO|WARNING|ERROR]
     */
    public static void log(int errorLevel, String message, Throwable e, boolean printToConsole) {
        if(printToConsole){
        	if(errorLevel == IStatus.ERROR){
        		System.out.println("Error received...");
        	}else{
        		System.out.println("Log received...");
        	}
            System.out.println(message);
            System.err.println(message);
            if(e != null){
            	e.printStackTrace();
            }
        }
        
        try {
	        Status s = new Status(errorLevel, getPluginID(), errorLevel, message, e);
	        getDefault().getLog().log(s);
        } catch (Exception e1) {
            //logging should not fail!
        }
    }

    public static void log(IStatus status) {
        getDefault().getLog().log(status);
    }
    
    public static void log(Throwable e) {
        log(e, true);
    }
    
    public static void log(Throwable e, boolean printToConsole) {
        log(IStatus.ERROR, e.getMessage() != null ? e.getMessage() : "No message gotten.", e, printToConsole);
    }

    public static void logInfo(String msg) {
        IStatus s = PydevPlugin.makeStatus(IStatus.INFO, msg, null);
        PydevPlugin plug = getDefault();
        if(plug == null){//testing mode
            System.out.println(msg);
        }else{
            plug.getLog().log(s);
        }
    }
    
    public static CoreException log(String msg) {
        IStatus s = PydevPlugin.makeStatus(IStatus.ERROR, msg, new RuntimeException(msg));
        CoreException e = new CoreException(s);
        PydevPlugin.log(e);
        return e;
    }

    public static IEditorPart doOpenEditor(IFile f, boolean activate) {
    	if (f == null)
    		return null;
    	
    	try {
    		FileEditorInput file = new FileEditorInput(f);
    		return openEditorInput(file);
    		
    	} catch (Exception e) {
    		log(IStatus.ERROR, "Unexpected error opening path " + f.toString(), e);
    		return null;
    	}
    }
    
    /**
     * Utility function that opens an editor on a given path within a zip file.
     * 
     * @return part that is the editor
     */
    public static IEditorPart doOpenEditor(File zipFile, String zipFilePath, boolean activate) {
        if (zipFile == null || zipFilePath == null){
            return null;
        }
        
        try {
            IEditorInput file = new PydevZipFileEditorInput(new PydevZipFileStorage(zipFile, zipFilePath));
            return openEditorInput(file);
            
        } catch (Exception e) {
            log(IStatus.ERROR, "Unexpected error opening zip file " + zipFile.getAbsolutePath()+ " - "+zipFilePath, e);
            return null;
        }
    }
    
    /**
     * Utility function that opens an editor on a given path.
     * 
     * @return part that is the editor
     */
    public static IEditorPart doOpenEditor(IPath path, boolean activate) {
        if (path == null){
            return null;
        }

        try {
    		IEditorInput file = createEditorInput(path);
	        return openEditorInput(file);
            
        } catch (Exception e) {
            log(IStatus.ERROR, "Unexpected error opening path " + path.toString(), e);
            return null;
        }
    }
    
    
	private static IEditorPart openEditorInput(IEditorInput file) throws PartInitException {
		final IWorkbench workbench = plugin.getWorkbench();
		if(workbench == null){
			throw new RuntimeException("workbench cannot be null");
		}

		IWorkbenchWindow activeWorkbenchWindow = workbench.getActiveWorkbenchWindow();
		if(activeWorkbenchWindow == null){
			throw new RuntimeException("activeWorkbenchWindow cannot be null (we have to be in a ui thread for this to work)");
		}
		
		IWorkbenchPage wp = activeWorkbenchWindow.getActivePage();
      
		// File is inside the workspace
		return IDE.openEditor(wp, file, PyEdit.EDITOR_ID);
	}

    
    
    

//  =====================
//  ===================== ALL BELOW IS COPIED FROM org.eclipse.ui.internal.editors.text.OpenExternalFileAction
//  =====================

    public static IEditorInput createEditorInput(IPath path) {
        return createEditorInput(path, true);
    }
    /**
     * @param path
     * @return
     */
    private static IEditorInput createEditorInput(IPath path, boolean askIfDoesNotExist) {
        IEditorInput edInput = null;
        IWorkspace w = ResourcesPlugin.getWorkspace();      

        //let's start with the 'easy' way
    	IFile fileForLocation = w.getRoot().getFileForLocation(path);
    	if(fileForLocation != null){
    		return new FileEditorInput(fileForLocation);
    	}

        
        
        IFile files[] = w.getRoot().findFilesForLocation(path);
        if (files == null  || files.length == 0 || !files[0].exists()){
            //it is probably an external file
            File systemFile = path.toFile();
            if(systemFile.exists()){
                edInput = createEditorInput(systemFile);
                
            }else if(askIfDoesNotExist){
                //this is the last resort... First we'll try to check for a 'good' match,
                //and if there's more than one we'll ask it to the user
                List<IFile> likelyFiles = getLikelyFiles(path, w);
                IFile iFile = selectWorkspaceFile(likelyFiles.toArray(new IFile[0]));
                if(iFile != null){
                    return new FileEditorInput(iFile);
                }
                
                //ok, ask the user for any file in the computer
                IEditorInput input = selectFilesystemFileForPath(path);
                if(input != null){
                    return input;
                }
            }
        }else{ //file exists
            edInput = doFileEditorInput(selectWorkspaceFile(files));
        }
        return edInput;
    }

    private static IEditorInput doFileEditorInput(IFile file) {
        if(file == null){
            return null;
        }
        return new FileEditorInput(file);
    }
    /**
     * This is the last resort... pointing to some filesystem file to get the editor for some path.
     */
    private static IEditorInput selectFilesystemFileForPath(final IPath path) {
        final List<String> l = new ArrayList<String>();
        Runnable r = new Runnable(){

            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                FileDialog dialog = new FileDialog(shell);
                dialog.setText(path+" - select correspondent filesystem file.");
                dialog.setFilterExtensions(FileTypesPreferencesPage.getWildcardValidSourceFiles());
                String string = dialog.open();
                if(string != null){
                    l.add(string);
                }
            }
        };
        if(Display.getCurrent() == null){ //not ui-thread
            Display.getDefault().syncExec(r);
        }else{
            r.run();
        }
        if(l.size() > 0){
            String fileAbsolutePath = REF.getFileAbsolutePath(l.get(0));
            return new PydevFileEditorInput(new File(fileAbsolutePath));
        }
        return null;
    }
    
    /**
     * This method will pass all the files in the workspace and check if there's a file that might
     * be a match to some path (use only as an almost 'last-resort').
     */
    private static List<IFile> getLikelyFiles(IPath path, IWorkspace w) {
        List<IFile> ret = new ArrayList<IFile>();
        try {
            IResource[] resources = w.getRoot().members();
            getLikelyFiles(path, ret, resources);
        } catch (CoreException e) {
            Log.log(e);
        }
        return ret;
    }
    
    /**
     * Used to recursively get the likely files given the first set of containers
     */
    private static void getLikelyFiles(IPath path, List<IFile> ret, IResource[] resources) throws CoreException {
        String strPath = path.removeFileExtension().lastSegment().toLowerCase(); //this will return something as 'foo'
        
        for (IResource resource : resources) {
            if(resource instanceof IFile){
                IFile f = (IFile) resource;
                
                if(PythonPathHelper.isValidSourceFile(f)){
                    if(resource.getFullPath().removeFileExtension().lastSegment().toLowerCase().equals(strPath)){ 
                        ret.add((IFile) resource);
                    }
                }
            }else if(resource instanceof IContainer){
                getLikelyFiles(path, ret, ((IContainer)resource).members());
            }
        }
    }
    
    private static IEditorInput createEditorInput(File file) {
        IFile[] workspaceFile= getWorkspaceFiles(file);
        if (workspaceFile != null && workspaceFile.length > 0){
            IFile file2 = selectWorkspaceFile(workspaceFile);
            if(file2 != null){
                return new FileEditorInput(file2);
            }else{
                return new FileEditorInput(workspaceFile[0]);
            }
        }
        return new PydevFileEditorInput(file);
    }

    
    public static IFile getWorkspaceFile(File file) {
        IFile[] files = getWorkspaceFiles(file);
        return selectWorkspaceFile(files);
    }
    
    public static IFile[] getWorkspaceFiles(File file) {
        IWorkspace workspace= ResourcesPlugin.getWorkspace();
        IPath location= Path.fromOSString(file.getAbsolutePath());
        IFile[] files= workspace.getRoot().findFilesForLocation(location);
        files= filterNonExistentFiles(files);
        if (files == null || files.length == 0){
            return null;
        }
        
        return files;
    }
    

    private static IFile[] filterNonExistentFiles(IFile[] files){
        if (files == null)
            return null;

        int length= files.length;
        ArrayList<IFile> existentFiles= new ArrayList<IFile>(length);
        for (int i= 0; i < length; i++) {
            if (files[i].exists())
                existentFiles.add(files[i]);
        }
        return (IFile[])existentFiles.toArray(new IFile[existentFiles.size()]);
    }
    
    private static IFile selectWorkspaceFile(final IFile[] files) {
        if(files == null || files.length == 0){
            return null;
        }
        if(files.length == 1){
            return files[0];
        }
        final List<IFile> selected = new ArrayList<IFile>();
        
        Runnable r = new Runnable(){
            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                ElementListSelectionDialog dialog= new ElementListSelectionDialog(shell, new PyFileLabelProvider());
                dialog.setElements(files);
                dialog.setTitle("Select Workspace File");
                dialog.setMessage("File may be matched to multiple files in the workspace.");
                if (dialog.open() == Window.OK){
                    selected.add((IFile) dialog.getFirstResult());
                }
            }
            
        };
        if(Display.getCurrent() == null){ //not ui-thread
            Display.getDefault().syncExec(r);
        }else{
            r.run();
        }
        if(selected.size() > 0){
            return selected.get(0);
        }
        return null;
    }

    
//  =====================
//  ===================== END COPY FROM org.eclipse.ui.internal.editors.text.OpenExternalFileAction
//  =====================


    /**
	 * Returns this plug-in's template store.
	 * 
	 * @return the template store of this plug-in instance
	 */
    public TemplateStore getTemplateStore() {
        if (fStore == null) {
            fStore = new ContributionTemplateStore(getContextTypeRegistry(), getPreferenceStore(), CUSTOM_TEMPLATES_PY_KEY);
            try {
                fStore.load();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return fStore;
    }

    /**
     * Returns this plug-in's context type registry.
     * 
     * @return the context type registry for this plug-in instance
     */
    public ContextTypeRegistry getContextTypeRegistry() {
        if (fRegistry == null) {
            // create an configure the contexts available in the template editor
            fRegistry = new ContributionContextTypeRegistry();
            fRegistry.addContextType(PyContextType.PY_CONTEXT_TYPE);
        }
        return fRegistry;
    }

    
    
    /**
     * @return the script to get the variables.
     * 
     * @throws CoreException
     */
    public static File getScriptWithinPySrc(String targetExec) throws CoreException {
        IPath relative = new Path("PySrc").addTrailingSeparator().append(targetExec);
        return PydevPlugin.getBundleInfo().getRelativePath(relative);
    }

    /**
     * @return the cache that should be used to access images within the pydev plugin.
     */
    public static ImageCache getImageCache(){
        return PydevPlugin.getBundleInfo().getImageCache();
    }
    
    
    //Images for the console
    private static final String[][] IMAGES = new String[][] { { "icons/save.gif", //$NON-NLS-1$
            ScriptConsoleUIConstants.SAVE_SESSION_ICON }, { "icons/terminate.gif", //$NON-NLS-1$
            ScriptConsoleUIConstants.TERMINATE_ICON } };

    @Override
    protected void initializeImageRegistry(ImageRegistry registry) {
        for (int i = 0; i < IMAGES.length; ++i) {
            URL url = getDefault().getBundle().getEntry(IMAGES[i][0]);
            registry.put(IMAGES[i][1], ImageDescriptor.createFromURL(url));
        }
    }

    public ImageDescriptor getImageDescriptor(String key) {
        return getImageRegistry().getDescriptor(key);
    }
    //End Images for the console

    

    /** Listener list **/
    private List testListeners = new ArrayList();


	@SuppressWarnings("unchecked")
    public void addTestListener(ITestRunListener listener) {
		testListeners.add(listener);
	}
	
	public void removeTestListener(ITestRunListener listener) {
		testListeners.remove(listener);
	}

	public List getListeners() {
		return testListeners;
	}
	
	public void runTests(String moduleDir, String moduleName, IProject project) throws IOException, CoreException {
		new PyUnitTestRunner().runTests(moduleDir, moduleName, project);
	}
	
	public void fireTestsStarted(int count) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testsStarted(count);
		}
	}

	public void fireTestsFinished() {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testsFinished();
		}
	}

	public void fireTestStarted(String klass, String methodName) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testStarted(klass, methodName);
		}
	}

	public void fireTestFailed(String klass, String methodName, String trace) {
		for (Iterator all=getListeners().iterator(); all.hasNext();) {
			ITestRunListener each = (ITestRunListener) all.next();
			each.testFailed(klass, methodName, trace);
		}
	}
	
	
    /**
     * @param file the file we want to get info on.
     * @return a tuple with the pythonnature to be used and the name of the module represented by the file in that scenario.
     */
    public static Tuple<SystemPythonNature, String> getInfoForFile(File file){
        String modName = null;
        IInterpreterManager pythonInterpreterManager = getPythonInterpreterManager(false);
        IInterpreterManager jythonInterpreterManager = getJythonInterpreterManager(false);
        if(pythonInterpreterManager == null || jythonInterpreterManager == null){
            return null;
        }
    
        SystemPythonNature systemPythonNature = new SystemPythonNature(pythonInterpreterManager);
        SystemPythonNature pySystemPythonNature = systemPythonNature;
        SystemPythonNature jySystemPythonNature = null;
        try {
            modName = systemPythonNature.resolveModule(file);
        } catch (Exception e) {
            // that's ok
        }
        if(modName == null){
            systemPythonNature = new SystemPythonNature(jythonInterpreterManager);
            jySystemPythonNature = systemPythonNature;
            try {
                modName = systemPythonNature.resolveModule(file);
            } catch (Exception e) {
                // that's ok
            }
        }
        if(modName != null){
            return new Tuple<SystemPythonNature, String>(systemPythonNature, modName);
        }else{
            //unable to discover it
            try {
                // the default one is python (actually, this should never happen, but who knows)
                pythonInterpreterManager.getDefaultInterpreter();
                modName = getModNameFromFile(file);
                return new Tuple<SystemPythonNature, String>(pySystemPythonNature, modName);
            } catch (Exception e) {
                //the python interpreter manager is not valid or not configured
                try {
                    // the default one is jython
                    jythonInterpreterManager.getDefaultInterpreter();
                    modName = getModNameFromFile(file);
                    return new Tuple<SystemPythonNature, String>(jySystemPythonNature, modName);
                } catch (Exception e1) {
                    // ok, nothing to do about it, no interpreter is configured
                    return null;
                }
            }
        }
    }
    
    /**
     * This is the last resort (should not be used anywhere else).
     */
    private static String getModNameFromFile(File file) {
        if(file == null){
            return null;
        }
        String name = file.getName();
        int i = name.indexOf('.');
        if (i != -1){
            return name.substring(0, i);
        }
        return name;
    }

    /**
     * This is a preference store that combines the preferences for pydev with the general preferences for editors.
     */
    private static IPreferenceStore fChainedPrefStore;
    
    /**
     * @return a preference store that has the pydev preference store and the default editors text store
     */
    public synchronized static IPreferenceStore getChainedPrefStore() {
        if(fChainedPrefStore == null){
            IPreferenceStore general = EditorsUI.getPreferenceStore();
            IPreferenceStore preferenceStore = getDefault().getPreferenceStore();
            fChainedPrefStore = new ChainedPreferenceStore(new IPreferenceStore[] { preferenceStore, general });
        }
        return fChainedPrefStore;
    }
    
    /**
     * Given a resource get the string in the filesystem for it.
     */
    public static String getIResourceOSString(IResource f) {
        String fullPath = f.getRawLocation().toOSString();
        //now, we have to make sure it is canonical...
        File file = new File(fullPath);
        if(file.exists()){
            return REF.getFileAbsolutePath(file);
        }else{
            //it does not exist, so, we have to check its project to validate the part that we can
            IProject project = f.getProject();
            IPath location = project.getLocation();
            File projectFile = location.toFile();
            if(projectFile.exists()){
                String projectFilePath = REF.getFileAbsolutePath(projectFile);
                
                if(fullPath.startsWith(projectFilePath)){
                    //the case is all ok
                    return fullPath;
                }else{
                    //the case appears to be different, so, let's check if this is it...
                    if(fullPath.toLowerCase().startsWith(projectFilePath.toLowerCase())){
                        String relativePart = fullPath.substring(projectFilePath.length());
                        
                        //at least the first part was correct
                        return projectFilePath+relativePart;
                    }
                }
            }
        }
        
        //it may not be correct, but it was the best we could do...
        return fullPath;
    }
    
    /**
     * Writes to the workspace a given object (in the given filename)
     */
    public static void writeToWorkspaceMetadata(Object obj, String fileName) {
        Bundle bundle = Platform.getBundle("org.python.pydev");
        IPath path = Platform.getStateLocation( bundle );       
        path = path.addTrailingSeparator();
        path = path.append(fileName);
        try {
            FileOutputStream out = new FileOutputStream(path.toFile());
            REF.writeToStreamAndCloseIt(obj, out);
            
        } catch (Exception e) {
            PydevPlugin.log(e);
            throw new RuntimeException(e);
        }               
    }

    /**
     * Loads from the workspace metadata a given object (given the filename)
     */
    public static Object readFromWorkspaceMetadata(String fileName) {
        Bundle bundle = Platform.getBundle("org.python.pydev");
        IPath path = Platform.getStateLocation( bundle );       
        path = path.addTrailingSeparator();
        path = path.append(fileName);
        
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(path.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return REF.readFromInputStreamAndCloseIt(new ICallback<Object, ObjectInputStream>(){

            public Object call(ObjectInputStream arg) {
                try{
                    return arg.readObject();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            }}, 
            
            fileInputStream);
    }
}