/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.components.properties.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opentcs.guing.model.ModelComponent;
import org.opentcs.guing.util.ResourceBundleUtil;

/**
 * Ein Property, das einen Wert aus einer gegebenen Menge von Werten annehmen
 * kann.
 * Beispiel: links aus der Menge {links, rechts},
 * Nord aus der Menge {Nord, S�d, Ost, West}
 *
 * @author Sebastian Naumann (ifak e.V. Magdeburg)
 * @param <E> The type of the enum.
 */
public class SelectionProperty<E extends Enum<E>>
    extends AbstractProperty
    implements Selectable<E> {

  /**
   * Die m�glichen Werte.
   */
  private List<E> fPossibleValues;

  /**
   * Standardkonstruktor.
   *
   * @param model
   */
  public SelectionProperty(ModelComponent model) {
    this(model, new ArrayList<>(), "");
  }

  /**
   * Creates a new instance of SelectionProperty
   *
   * @param model
   *
   * @param possibleValues
   * @param value
   */
  public SelectionProperty(ModelComponent model, List<E> possibleValues,
                           Object value) {
    super(model);
    setPossibleValues(possibleValues);
    fValue = value;
  }

  @Override
  public Object getComparableValue() {
    return fValue;
  }

  /**
   * Setzt die m�glichen Werte im Nachhinein.
   *
   * @param possibleValues Ein Array mit den m�glichen Werte.
   */
  @Override
  public void setPossibleValues(List<E> possibleValues) {
    fPossibleValues = Objects.requireNonNull(possibleValues, "possibleValues is null");
  }

  @Override
  public void setValue(Object value) {
    if (fPossibleValues.contains(value)
        || value.equals(ResourceBundleUtil.getBundle().getString("PropertiesCollection.differentValues.text"))) {
      fValue = value;
    }
  }

  @Override
  public String toString() {
    return getValue().toString();
  }

  @Override
  public List getPossibleValues() {
    return fPossibleValues;
  }

  @Override
  public void copyFrom(Property property) {
    SelectionProperty selectionProperty = (SelectionProperty) property;
    setValue(selectionProperty.getValue());
  }
}
