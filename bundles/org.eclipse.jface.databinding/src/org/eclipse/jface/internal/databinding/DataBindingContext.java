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
package org.eclipse.jface.internal.databinding;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.jface.databinding.BindSpec;
import org.eclipse.jface.databinding.BindingException;
import org.eclipse.jface.databinding.IBindSpec;
import org.eclipse.jface.databinding.IBindSupportFactory;
import org.eclipse.jface.databinding.IBinding;
import org.eclipse.jface.databinding.IChangeListener;
import org.eclipse.jface.databinding.IDataBindingContext;
import org.eclipse.jface.databinding.IUpdatable;
import org.eclipse.jface.databinding.IUpdatableCollection;
import org.eclipse.jface.databinding.IUpdatableFactory;
import org.eclipse.jface.databinding.IUpdatableTree;
import org.eclipse.jface.databinding.IUpdatableValue;
import org.eclipse.jface.databinding.NestedProperty;
import org.eclipse.jface.databinding.Property;
import org.eclipse.jface.databinding.TreeModelDescription;
import org.eclipse.jface.databinding.converter.IConverter;
import org.eclipse.jface.databinding.converterfunction.ConversionFunctionRegistry;
import org.eclipse.jface.databinding.converters.FunctionalConverter;
import org.eclipse.jface.databinding.converters.IdentityConverter;
import org.eclipse.jface.databinding.updatables.SettableValue;
import org.eclipse.jface.databinding.validator.IValidator;
import org.eclipse.jface.databinding.validator.ValidatorRegistry;
import org.eclipse.jface.util.Assert;

/**
 * 
 * @since 3.2
 */
public class DataBindingContext implements IDataBindingContext {

	private List createdUpdatables = new ArrayList();

	private DataBindingContext parent;

	private List partialValidationMessages = new ArrayList();

	private List validationMessages = new ArrayList();

	private SettableValue partialValidationMessage = new SettableValue(
			String.class, ""); //$NON-NLS-1$

	private SettableValue validationMessage = new SettableValue(String.class,
			""); //$NON-NLS-1$

	private SettableValue combinedValidationMessage = new SettableValue(
			String.class, ""); //$NON-NLS-1$

	private List factories = new ArrayList();

	private List bindSupportFactories = new ArrayList();

	protected int validationTime;

	protected int updateTime;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#addBindSupportFactory(org.eclipse.jface.databinding.IBindSupportFactory)
	 */
	public void addBindSupportFactory(IBindSupportFactory factory) {
		bindSupportFactories.add(factory);
	}

	/**
	 * 
	 */
	public DataBindingContext() {
		registerFactories();
		registerDefaultBindSupportFactory();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#dispose()
	 */
	public void dispose() {
		for (Iterator it = createdUpdatables.iterator(); it.hasNext();) {
			IUpdatable updatable = (IUpdatable) it.next();
			updatable.dispose();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#getCombinedValidationMessage()
	 */
	public IUpdatableValue getCombinedValidationMessage() {
		return combinedValidationMessage;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#getPartialValidationMessage()
	 */
	public IUpdatableValue getPartialValidationMessage() {
		return partialValidationMessage;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#getValidationMessage()
	 */
	public IUpdatableValue getValidationMessage() {
		return validationMessage;
	}

	protected void registerDefaultBindSupportFactory() {
		// Add the default bind support factory
		addBindSupportFactory(new IBindSupportFactory() {

			public IValidator createValidator(Class fromType, Class toType,
					Object modelDescription) {
				if (fromType == null || toType == null) {
					// System.err.println("FIXME: Boris, is this a bug? In
					// registerDefaultBindSupportFactory.addBindSupportFactory.createValidator:
					// fromType is null or toType is null!!!!!"); //$NON-NLS-1$
					// try {
					// throw new BindingException("Cannot create proper
					// IValidator."); //$NON-NLS-1$
					// } catch (BindingException e) {
					// e.printStackTrace();
					// System.err.println();
					// }
					return new IValidator() {

						public String isPartiallyValid(Object value) {
							return null;
						}

						public String isValid(Object value) {
							return null;
						}
					};
				}

				IValidator dataTypeValidator = ValidatorRegistry.getDefault()
						.get(fromType, toType);
				if (dataTypeValidator == null) {
					throw new BindingException(
							"No IValidator is registered for conversions from " + fromType.getName() + " to " + toType.getName()); //$NON-NLS-1$ //$NON-NLS-2$
				}
				return dataTypeValidator;
			}

			public IConverter createConverter(Class fromType, Class toType,
					Object modelDescription) {
				if (toType == null) {
					return null;
				}
				if (fromType == toType) {
					return new IdentityConverter(fromType, toType);
				}
				if (ConversionFunctionRegistry.canConvertPair(fromType, toType)) {
					return new FunctionalConverter(fromType, toType);
				}
				// FIXME: djo -- This doesn't always work in the case of object
				// types?
				if (toType.isAssignableFrom(fromType)
						|| fromType.isAssignableFrom(toType)) {
					return new IdentityConverter(fromType, toType);
				}
				return null;
			}
		});
	}

	protected void registerFactories() {
		addUpdatableFactory(new IUpdatableFactory() {
			public IUpdatable createUpdatable(Map properties,
					Object description, IDataBindingContext bindingContext) {
				if (description instanceof Property) {
					Property propertyDescription = (Property) description;
					Object o = propertyDescription.getObject();
					if (o instanceof IUpdatableValue) {
						IUpdatableValue updatableValue = (IUpdatableValue) o;
						Class propertyType = propertyDescription
								.getPropertyType();
						if (propertyType == null) {
							throw new BindingException(
									"Missing required property type for binding to a property of an IUpdatableValue."); //$NON-NLS-1$
						}
						Boolean isCollectionProperty = propertyDescription
								.isCollectionProperty();
						if (isCollectionProperty == null) {
							throw new BindingException(
									"Missing required property collection information for binding to a property of an IUpdatableValue."); //$NON-NLS-1$
						}
						Object propertyID = propertyDescription.getPropertyID();
						if (isCollectionProperty.booleanValue()) {
							return new NestedUpdatableCollection(
									DataBindingContext.this, updatableValue,
									propertyID, propertyType);
						}
						return new NestedUpdatableValue(
								DataBindingContext.this, updatableValue,
								propertyID, propertyType);
					} else if (o instanceof List) {
						return new ListUpdatableCollection(
								(List) o,
								propertyDescription.getPropertyType() == null ? Object.class
										: propertyDescription.getPropertyType());
					}
				} else if (description instanceof TreeModelDescription) {
					TreeModelDescription treeModelDescription = (TreeModelDescription) description;
					if (treeModelDescription.getRoot() != null) {
						if (treeModelDescription.getRoot() instanceof IUpdatable) {
							if (treeModelDescription.getRoot() instanceof IUpdatableTree)
								return (IUpdatableTree) treeModelDescription
										.getRoot();
							// Nest the TreeModelDescription's root
							return new NestedUpdatableTree(
									DataBindingContext.this,
									treeModelDescription);
						} else if (treeModelDescription.getRoot() instanceof Property) {
							// Create an Updatable for the
							// TreeModelDescription's root first
							TreeModelDescription newDescription = new TreeModelDescription(
									DataBindingContext.this
											.createUpdatable(treeModelDescription
													.getRoot()));
							Class[] types = treeModelDescription.getTypes();
							for (int i = 0; i < types.length; i++) {
								String[] props = treeModelDescription
										.getChildrenProperties(types[i]);
								for (int j = 0; j < props.length; j++)
									newDescription.addChildrenProperty(
											types[i], props[j]);
							}
							return DataBindingContext.this
									.createUpdatable(newDescription);
						}
					}
					return null;
				}
				return null;
			}
		});
	}

	private void removeValidationListenerAndMessage(List listOfPairs,
			Object first) {
		for (int i = listOfPairs.size() - 1; i >= 0; i--) {
			Pair pair = (Pair) listOfPairs.get(i);
			if (pair.a.equals(first)) {
				listOfPairs.remove(i);
				return;
			}
		}
		return;
	}

	/**
	 * @param listener
	 * @param partialValidationErrorOrNull
	 */
	public void updatePartialValidationError(IChangeListener listener,
			String partialValidationErrorOrNull) {
		removeValidationListenerAndMessage(partialValidationMessages, listener);
		if (partialValidationErrorOrNull != null) {
			partialValidationMessages.add(new Pair(listener,
					partialValidationErrorOrNull));
		}
		updateValidationMessage(
				combinedValidationMessage,
				partialValidationMessages.size() > 0 ? partialValidationMessages
						: validationMessages);
		updateValidationMessage(partialValidationMessage,
				partialValidationMessages);
	}

	/**
	 * @param listener
	 * @param validationErrorOrNull
	 */
	public void updateValidationError(IChangeListener listener,
			String validationErrorOrNull) {
		removeValidationListenerAndMessage(validationMessages, listener);
		if (validationErrorOrNull != null) {
			validationMessages.add(new Pair(listener, validationErrorOrNull));
		}
		updateValidationMessage(
				combinedValidationMessage,
				partialValidationMessages.size() > 0 ? partialValidationMessages
						: validationMessages);
		updateValidationMessage(validationMessage, validationMessages);
	}

	private void updateValidationMessage(
			SettableValue validationSettableMessage, List listOfPairs) {
		if (listOfPairs.size() == 0) {
			validationSettableMessage.setValue(""); //$NON-NLS-1$
		} else {
			validationSettableMessage.setValue(((Pair) listOfPairs
					.get(listOfPairs.size() - 1)).b);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#bind(org.eclipse.jface.databinding.IUpdatable,
	 *      org.eclipse.jface.databinding.IUpdatable,
	 *      org.eclipse.jface.databinding.IBindSpec)
	 */
	public IBinding bind(IUpdatable targetUpdatable, IUpdatable modelUpdatable,
			IBindSpec bindSpec) {
		Binding binding;
		if (bindSpec == null) {
			bindSpec = new BindSpec(null, null);
		}
		if (targetUpdatable instanceof IUpdatableValue) {
			if (modelUpdatable instanceof IUpdatableValue) {
				IUpdatableValue target = (IUpdatableValue) targetUpdatable;
				IUpdatableValue model = (IUpdatableValue) modelUpdatable;
				fillBindSpecDefaults(bindSpec, target.getValueType(), model
						.getValueType(), null);
				binding = new ValueBinding(this, target, model, bindSpec);
			} else {
				throw new BindingException(
						"incompatible updatables: target is value, model is " + modelUpdatable.getClass().getName()); //$NON-NLS-1$
			}
		} else if (targetUpdatable instanceof IUpdatableCollection) {
			if (modelUpdatable instanceof IUpdatableCollection) {
				IUpdatableCollection target = (IUpdatableCollection) targetUpdatable;
				IUpdatableCollection model = (IUpdatableCollection) modelUpdatable;
				fillBindSpecDefaults(bindSpec, target.getElementType(), model
						.getElementType(), null);
				binding = new CollectionBinding(this, target, model, bindSpec);
			} else {
				throw new BindingException(
						"incompatible updatables: target is collection, model is " + modelUpdatable.getClass().getName()); //$NON-NLS-1$
			}
		} else if (targetUpdatable instanceof IUpdatableTree) {
			if (modelUpdatable instanceof IUpdatableTree) {
				IUpdatableTree target = (IUpdatableTree) targetUpdatable;
				IUpdatableTree model = (IUpdatableTree) modelUpdatable;
				// Tree bindings does not provide conversions between level
				// elements (no bindSpec)
				binding = new TreeBinding(this, target, model);
			} else {
				throw new BindingException(
						"incompatible updatables: target is collection, model is " + modelUpdatable.getClass().getName()); //$NON-NLS-1$
			}

		} else {
			throw new BindingException("not yet implemented"); //$NON-NLS-1$
		}
		// DJO: Each binder is now responsible for adding its own change
		// listeners.
		// targetUpdatable.addChangeListener(binding);
		// modelUpdatable.addChangeListener(binding);
		binding.updateTargetFromModel();
		return binding;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#bind(java.lang.Object,
	 *      org.eclipse.jface.databinding.IUpdatable,
	 *      org.eclipse.jface.databinding.IBindSpec)
	 */
	public IBinding bind(Object targetDescription, IUpdatable modelUpdatable,
			IBindSpec bindSpec) {
		return bind(createUpdatable(targetDescription), modelUpdatable, bindSpec);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#bind(org.eclipse.jface.databinding.IUpdatable,
	 *      java.lang.Object, org.eclipse.jface.databinding.IBindSpec)
	 */
	public IBinding bind(IUpdatable targetUpdatable, Object modelDescription,
			IBindSpec bindSpec) {
		if (bindSpec == null) {
			bindSpec = new BindSpec(null, null);
		}
		Class fromType = null;
		if (targetUpdatable instanceof IUpdatableValue) {
			fromType = ((IUpdatableValue) targetUpdatable).getValueType();
		} else if (targetUpdatable instanceof IUpdatableCollection) {
			fromType = ((IUpdatableCollection) targetUpdatable)
					.getElementType();
		}
		fillBindSpecDefaults(bindSpec, fromType, null, modelDescription);
		return bind(targetUpdatable, createUpdatable(modelDescription), bindSpec);
	}

	protected void fillBindSpecDefaults(IBindSpec bindSpec, Class fromType,
			Class toType, Object modelDescriptionOrNull) {
		if (bindSpec.getValidator() == null) {
			((BindSpec) bindSpec).setValidator(createValidator(fromType,
					toType, modelDescriptionOrNull));
		}
		if (bindSpec.getConverter() == null) {
			((BindSpec) bindSpec).setConverter(createConverter(fromType,
					toType, modelDescriptionOrNull));
		}
	}

	public IValidator createValidator(Class fromType, Class toType,
			Object modelDescription) {
		for (int i = bindSupportFactories.size() - 1; i >= 0; i--) {
			IBindSupportFactory bindSupportFactory = (IBindSupportFactory) bindSupportFactories
					.get(i);
			IValidator validator = bindSupportFactory.createValidator(fromType,
					toType, modelDescription);
			if (validator != null) {
				return validator;
			}
		}
		if (parent != null) {
			return parent.createValidator(fromType, toType, modelDescription);
		}
		return null;
	}

	public IConverter createConverter(Class fromType, Class toType,
			Object modelDescription) {
		for (int i = bindSupportFactories.size() - 1; i >= 0; i--) {
			IBindSupportFactory bindSupportFactory = (IBindSupportFactory) bindSupportFactories
					.get(i);
			IConverter converter = bindSupportFactory.createConverter(fromType,
					toType, modelDescription);
			if (converter != null) {
				return converter;
			}
		}
		if (parent != null) {
			return parent.createConverter(fromType, toType, modelDescription);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#bind(java.lang.Object,
	 *      java.lang.Object, org.eclipse.jface.databinding.IBindSpec)
	 */
	public IBinding bind(Object targetDescription, Object modelDescription,
			IBindSpec bindSpec) {
		return bind(createUpdatable(targetDescription), modelDescription, bindSpec);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#createUpdatable(java.lang.Object)
	 */
	public IUpdatable createUpdatable(Object description) {
		IUpdatable updatable = doCreateUpdatable(description, this);
		if (updatable != null) {
			createdUpdatables.add(updatable);
		}
		return updatable;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#createNestedUpdatable(org.eclipse.jface.databinding.NestedProperty)
	 */
	public IUpdatable createNestedUpdatable(NestedProperty nestedProperty) {
		IUpdatable lastChildUpdatable = null;
		Object targetObject = nestedProperty.getObject();
		if (nestedProperty.getPrototypeClass() != null) {
			Class targetClazz = nestedProperty.getPrototypeClass();
			StringTokenizer tokenizer = new StringTokenizer((String) nestedProperty.getPropertyID(), "."); //$NON-NLS-1$
			while (tokenizer.hasMoreElements()) {
				String nextDesc = (String) tokenizer.nextElement();
				try {
					BeanInfo beanInfo = Introspector.getBeanInfo(targetClazz);
					PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
					Class discoveredClazz = null;
					for (int i = 0; i < propertyDescriptors.length; i++) {
						PropertyDescriptor descriptor = propertyDescriptors[i];
						if (descriptor.getName().equals(
								nextDesc)) {
							discoveredClazz = descriptor.getPropertyType();
							break;
						}
					}
					if (discoveredClazz != null) {
						targetClazz = discoveredClazz;
					} else {
						throw new BindingException("Error using prototype class to determine binding types."); //$NON-NLS-1$
					}
				} catch (BindingException be) {
					throw be;
				} catch (Exception e) {
					e.printStackTrace();
					throw new BindingException("Exeception using prototype class to determine binding types.", e); //$NON-NLS-1$
				}
				lastChildUpdatable = createUpdatable(new Property(targetObject,
						nextDesc, targetClazz, new Boolean(false)));
				targetObject = lastChildUpdatable;
			}

		} else {
			String[] properties = (String[]) nestedProperty.getPropertyID();
			for (int i = 0; i < properties.length; i++) {
				String nextDesc = properties[i];
				Class clazz = nestedProperty.getTypes()[i];
				lastChildUpdatable = createUpdatable(new Property(targetObject,
						nextDesc, clazz, new Boolean(false)));
				targetObject = lastChildUpdatable;
			}
		}
		return lastChildUpdatable;
	}

	protected IUpdatable doCreateUpdatable(Object description,
			DataBindingContext thisDatabindingContext) {
		for (int i = factories.size() - 1; i >= 0; i--) {
			IUpdatableFactory factory = (IUpdatableFactory) factories.get(i);
			IUpdatable result = factory.createUpdatable(null, description,
					thisDatabindingContext);
			if (result != null) {
				return result;
			}
		}
		if (parent != null) {
			return parent
					.doCreateUpdatable(description, thisDatabindingContext);
		}
		throw new BindingException("could not find updatable for " //$NON-NLS-1$
				+ description);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.databinding.IDataBindingContext#addUpdatableFactory(org.eclipse.jface.databinding.IUpdatableFactory)
	 */
	public void addUpdatableFactory(IUpdatableFactory updatableFactory) {
		// TODO: consider the fact that adding new factories for a given
		// description
		// may hide default ones (e.g., a new PropertyDescriptor may overide the
		// ond for EMF)
		factories.add(updatableFactory);
	}

	public void updateTargets() {
		Assert.isTrue(false, "updateTargets is not yet implemented"); //$NON-NLS-1$
	}

	public void updateModels() {
		Assert.isTrue(false, "updateModels is not yet implemented"); //$NON-NLS-1$
	}

}
