/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.ui.internal;

import org.eclipse.ui.statushandling.AbstractStatusHandler;
import org.eclipse.ui.statushandling.StatusHandlingState;

/**
 * A proxy handler which passes all statuses to handler assigned to current
 * application workbench advisor.
 * 
 * @since 3.3
 */
public class WorkbenchErrorHandlerProxy extends AbstractStatusHandler {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.errors.AbstractErrorHandler#handle(org.eclipse.ui.errors.HandlingStatusState)
	 */
	public void handle(StatusHandlingState handlingState) {
		Workbench.getInstance().getAdvisor().getWorkbenchErrorHandler().handle(
				handlingState);
	}
}
