/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.databinding;

import java.util.ArrayList;
import java.util.List;


/**
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is no guarantee that this API will remain
 * unchanged during the 3.2 release cycle. Please do not use this API without
 * consulting with the Platform/UI team.
 * </p>
 * 
 * A domain model has to implement this interface in order to establish a tree
 * binding. It is possible that the domain model itself is not organized as a tree and
 * requires specific logic that is beyond the support of a <code>TreeModelDescription</code>
 * 
 * 
 * @see TreeModelDescription for a simpler way to bind a tree. 
 * 
 * @since 3.2
 * 
 */
public interface ITree  {
		
	/**
	 * This is a helper class for ITree implementors and simplify the change notification
	 * handling.
	 * 
	 * @since 3.2
	 *
	 */
	public static class ChangeSupport {
		private ITree source;
		private List listeners = null;
		
		/**
		 * @param source tree
		 */
		public ChangeSupport(ITree source){
			this.source=source;
		}
		
		/**
		 * @param listener
		 */
		public void addTreeChangeListener(IChangeListener listener) {
			if (listener!=null) {
				if (listeners==null)
					listeners=new ArrayList();
				listeners.add(listener);
			}
		}
		
		/**
		 * @param listener
		 */
		public void removeTreeChangeListener(IChangeListener listener) {
			if (listener==null || listeners==null)
				return;
			listeners.remove(listener);
		}
		
		/**
		 * @param changeType
		 * @param oldValue
		 * @param newValue
		 * @param parent
		 * @param index
		 */
		public void fireTreeChange(int changeType, Object oldValue, Object newValue, Object parent, int index) {
			ChangeEvent evt = new ChangeEvent(source, changeType, oldValue, newValue, parent, index);
			fireTreeChange(evt);
		}
		
		/**
		 * @param evt
		 */
		public void fireTreeChange(ChangeEvent evt) {
			Object oval = evt.getOldValue();
			Object nval = evt.getNewValue();
			
			if (listeners==null ||
				(oval != null && nval != null && oval.equals(nval)))
				return;
			
			IChangeListener[] list = (IChangeListener[])listeners.toArray(new IChangeListener[listeners.size()]);
			for (int i = 0; i < list.length; i++) {
				list[i].handleChange(evt);
			}
		}
	}
	
		
	/**
	 * Returns the children elements of the given parent element.
	 *
	 * @param parentElement the parent element, <code>null</code> for root elements
	 * @return an array of child elements
	 */

	public Object[] getChildren(Object parentElement);
	
	/**
	 * @param parentElement or <code>null</code> for root elements
	 * @param children
	 */
	public void setChildren(Object parentElement, Object[] children);

	/**
	 * Returns whether the given element has children.
	 *
	 * @param element the element
	 * @return <code>true</code> if the given element has children,
	 *  and <code>false</code> if it has no children
	 */
	public boolean hasChildren(Object element);
	
    /**
     * The implementor of an ITree is responsible to provide the event
     * model for changes on the tree's shape and content.  It should be using 
     * <code>ITree.ChangeEvent</code> to notify the listener of any changes to the tree.
     * 
     * @param listener
     */
    public void addTreeChangeListener(IChangeListener listener);
    
    /**
     * @param listener
     */
    public void removeTreeChangeListener(IChangeListener listener);
    
    /** 
     *
     */
    public void dispose();

	
	/**
	 * @return types of all tree nodes that are expected on this tree.
	 */
	public Class[] getTypes();

}
