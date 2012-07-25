package org.python.pydev.dltk.console.codegen;

/**
 * Interface for extracting an object as a snippet of Python code suitable for
 * dropping into a Python console.
 * <p>
 * The expectation is that in the future extra functionality may be desired, for
 * example the generator may want direct access to the IInterpreterInfo, or a
 * way to force an additional import statement. These additions can be added in
 * the future (to a future child interface such as IScriptConsoleCodeGenerator2)
 */
public interface IScriptConsoleCodeGenerator {

    /**
     * Return a snippet of Python that represents this object.
     * @return Python code or null if no suitable code snippet can be created
     */
    public String getPyCode();

    /**
     * Return whether getPyCode has contents. This method should be fast as
     * it may be called multiple times during a single drag and drop.
     * 
     * @return whether or not Python code is available from getPyCode. 
     */
    public boolean hasPyCode();
}
