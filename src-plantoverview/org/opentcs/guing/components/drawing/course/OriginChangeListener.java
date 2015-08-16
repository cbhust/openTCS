/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */

package org.opentcs.guing.components.drawing.course;

import java.util.EventObject;

/**
 * Interface f�r Klassen, die an �nderungen des Koordinaten-Ursprungs
 * interessiert sind.
 *
 * @author Sebastian Naumann (ifak e.V. Magdeburg)
 */
public interface OriginChangeListener {

  /**
   * Nachricht, dass sich die Position des Ursprungs ge�ndert hat.
   *
   * @param evt das Ereignis
   */
  void originLocationChanged(EventObject evt);

  /**
   * Nachricht, dass sich der Ma�stab ge�ndert hat.
   *
   * @param evt das Ereignis
   */
  void originScaleChanged(EventObject evt);
}
