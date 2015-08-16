/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */

package org.opentcs.guing.components.properties;

import javax.swing.JComponent;
import org.opentcs.guing.application.action.edit.UndoRedoManager;
import org.opentcs.guing.model.ModelComponent;

/**
 * Ein Inhalt f�r eine Swing-Komponente, der �ber Eigenschaften eines
 * ModelComponent-Objekt Auskunft gibt und M�glichkeiten bietet, diese
 * Eigenschaften zu ver�ndern.
 *
 * @author Sebastian Naumann (ifak e.V. Magdeburg)
 */
public interface AttributesContent {

  /**
   * @param model Das ModelComponent-Objekt, dessen Eigenschaften dargestellt 
   * werden sollen.
   */
  void setModel(ModelComponent model);

  /**
   * Setzt die Anzeige zur�ck, wenn kein ModelComponente-Objekt mehr dargestellt
   * werden soll.
   */
  void reset();

  /**
   * Liefert den Inhalt, der in eine andere Swing-Komponente eingebunden werden
   * kann.
   *
   * @return
   */
  JComponent getComponent();

  /**
   * Liefert eine Beschreibung des Inhalts, der bei Aktivierung in der
   * �bergeordneten Swing-Komponente angezeigt werden kann.
   *
   * @return
   */
  String getDescription();

  /**
   * Initialisiert den Inhalt mit dem Parent und dem UndoManager.
   *
   * @param undoRedoManager
   */
  void setup(UndoRedoManager undoRedoManager);
}
