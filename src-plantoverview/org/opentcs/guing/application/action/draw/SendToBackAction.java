/**
 * (c): IML, JHotDraw.
 *
 * Changed by IML to allow access to ResourceBundle.
 *
 *
 * @(#)SendToBackAction.java
 *
 * Copyright (c) 2003-2008 by the original authors of JHotDraw and all its
 * contributors. All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with the copyright holders. For details
 * see accompanying license terms.
 */
package org.opentcs.guing.application.action.draw;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.DrawingView;
import org.jhotdraw.draw.Figure;
import org.jhotdraw.draw.action.AbstractSelectedAction;
import org.opentcs.guing.util.ResourceBundleUtil;

/**
 * SendToBackAction.
 *
 * @author Werner Randelshofer
 */
public class SendToBackAction
    extends AbstractSelectedAction {

  public final static String ID = "edit.sendToBack";

  /**
   * Creates a new instance.
   */
  public SendToBackAction(DrawingEditor editor) {
    super(editor);
    ResourceBundleUtil labels = ResourceBundleUtil.getBundle();
    labels.configureAction(this, ID, false);
    updateEnabledState();
  }

  @Override
  public void actionPerformed(java.awt.event.ActionEvent e) {
    final DrawingView view = getView();
    final LinkedList<Figure> figures = new LinkedList<>(view.getSelectedFigures());
    sendToBack(view, figures);
    fireUndoableEditHappened(new AbstractUndoableEdit() {
      @Override
      public String getPresentationName() {
        ResourceBundleUtil labels = ResourceBundleUtil.getBundle();
        return labels.getTextProperty(ID);
      }

      @Override
      public void redo() throws CannotRedoException {
        super.redo();
        SendToBackAction.sendToBack(view, figures);
      }

      @Override
      public void undo() throws CannotUndoException {
        super.undo();
        BringToFrontAction.bringToFront(view, figures);
      }
    });
  }

  public static void sendToBack(DrawingView view, Collection figures) {
    Iterator i = figures.iterator();
    Drawing drawing = view.getDrawing();

    while (i.hasNext()) {
      Figure figure = (Figure) i.next();
      drawing.sendToBack(figure);
    }
  }
}
