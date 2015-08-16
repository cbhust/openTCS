/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */

package org.opentcs.guing.application;

import org.opentcs.guing.model.FiguresFolder;
import org.opentcs.guing.model.ModelComponent;

/**
 * Provides some central services for various parts of the plant overview
 * application.
 *
 * @author Heinz Huber (Fraunhofer IML)
 * @author Stefan Walter (Fraunhofer IML)
 */
public interface GuiManager {

  /**
   * Wird aufgerufen, wenn das Objekt im Baum selektiert wurde.
   *
   * @param modelComponent A folder in the system model tree
   */
  void selectModelComponent(ModelComponent modelComponent);

  /**
   * Wird aufgerufen, wenn mehrere Objekte im Baum selektiert wurden.
   *
   * @param modelComponent
   */
  void addSelectedModelComponent(ModelComponent modelComponent);

  /**
   * Wird aufgerufen, wenn das Objekt aus dem Baum entfernt wurde (aufgrund
   * einer Nutzereingabe).
   *
   * @param fDataObject
   * @return true, wenn das Objekt entfernt wurde
   */
  boolean treeComponentRemoved(ModelComponent fDataObject);

  /**
   * Informiert die Applikation, dass im TreeView ein Figure-Objekt doppelt
   * angeklickt wurde.
   *
   * @param modelComponent
   */
  void figureSelected(ModelComponent modelComponent);

  /**
   * Wird aufgerufen, wenn im TreeView eine Blockstrecke doppelt angeklickt
   * wurde. Selektiert alle Figures im DrawingView, die zu der Blockstrecke
   * geh�ren.
   *
   * @param blockFiguresFolder
   */
  void blockSelected(FiguresFolder blockFiguresFolder);

  /**
   * L�scht alles aus dem openTCS-Kern und f�hrt Initialisierungen zu einem neu
   * erstellten Modell durch. Das Modell muss bei Aufruf dieser Methode bereits
   * erzeugt sein.
   */
  void createEmptyModel();

  /**
   * L�dt ein Fahrkurs-Modell vom Kernel
   */
  void loadModel();

  /**
   * @return
   */
  boolean saveModel();
  
  /**
   * 
   * @return 
   */
  boolean saveModelAs();

  /**
   * Erzeugt eine neues ModelComponent-Objekt, f�r das es kein Figure-Pendant
   * gibt. Hierzu geh�ren Blockbereich, Fahrzeug und Stationstyp.
   *
   * @param clazz Der Typ des zu erzeugenden Objekts.
   * @return das erzeugte ModelComponent-Objekt
   */
  ModelComponent createModelComponent(Class<? extends ModelComponent> clazz);
}
