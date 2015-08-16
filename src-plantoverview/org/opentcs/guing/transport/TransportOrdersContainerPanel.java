/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.transport;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Set;
import java.util.Vector;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.engio.mbassy.listener.Handler;
import org.opentcs.access.CredentialsException;
import org.opentcs.access.Kernel;
import org.opentcs.access.SharedKernelProvider;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.order.DriveOrder;
import org.opentcs.data.order.OrderSequence;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.guing.components.dialogs.DialogContent;
import org.opentcs.guing.components.dialogs.StandardContentDialog;
import org.opentcs.guing.event.SystemModelTransitionEvent;
import org.opentcs.guing.event.TransportOrderEvent;
import org.opentcs.guing.exchange.TransportOrderUtil;
import org.opentcs.guing.util.IconToolkit;
import org.opentcs.guing.util.ResourceBundleUtil;

/**
 * Eine Ansicht f�r Transportauftr�ge. Teil dieser Ansicht ist die Tabelle, in
 * der die Transportauftr�ge dargestellt sind. Noch zu tun: diese beiden
 * zusammenlegen.
 *
 * @author Sven Liebing (ifak e.V. Magdeburg)
 * @author Sebastian Naumann (ifak e.V. Magdeburg)
 * @author Stefan Walter (Fraunhofer IML)
 */
public class TransportOrdersContainerPanel
    extends JPanel {

  private static final String fIconPath = "/org/opentcs/guing/res/symbols/panel/";
  /**
   * Provides access to a kernel.
   */
  private final SharedKernelProvider kernelProvider;
  /**
   * A helper for creating transport orders with the kernel.
   */
  private final TransportOrderUtil orderUtil;
  /**
   * Provides panels for entering new transport orders.
   */
  private final Provider<CreateTransportOrderPanel> orderPanelProvider;
  /**
   * Die Tabelle, in der die Transportauftr�ge dargestellt werden.
   */
  private JTable fTable;
  /**
   * Das TableModel.
   */
  private FilterTableModel fTableModel;
  /**
   * Die Liste der Filterbuttons.
   */
  private List<FilterButton> fFilterButtons;
  /**
   * Die Transportauftr�ge.
   */
  private Vector<TransportOrder> fTransportOrders;

  /**
   * Creates a new instance of TransportOrdersView.
   *
   * @param kernelProvider Provides a access to a kernel.
   * @param orderUtil A helper for creating transport orders with the kernel.
   * @param orderPanelProvider Provides panels for entering new transport orders.
   */
  @Inject
  public TransportOrdersContainerPanel(SharedKernelProvider kernelProvider,
                                       TransportOrderUtil orderUtil,
                                       Provider<CreateTransportOrderPanel> orderPanelProvider) {
    this.kernelProvider = requireNonNull(kernelProvider, "kernelProvider");
    this.orderUtil = requireNonNull(orderUtil, "orderUtil");
    this.orderPanelProvider = requireNonNull(orderPanelProvider,
                                             "orderPanelProvider");
    initComponents();
  }

  @Handler
  public void handleSystemModelTransition(SystemModelTransitionEvent evt) {
    switch (evt.getStage()) {
      case UNLOADING:
        // XXX Clear panel?
        break;
      case LOADED:
        initView();
        break;
      default:
      // Do nada.
    }
  }

  private Kernel getKernel() {
    return kernelProvider.getKernel();
  }

  /**
   * Initializes this panel's contents.
   */
  public void initView() {
    setTransportOrders(fetchOrdersIfOnline());
  }

  private Set<TransportOrder> fetchOrdersIfOnline() {
    if (kernelProvider.kernelShared()) {
      return kernelProvider.getKernel().getTCSObjects(TransportOrder.class);
    }
    else {
      return new HashSet<>();
    }
  }

  /**
   * Initialisiert die verschiedenen Komponenten.
   */
  private void initComponents() {
    setLayout(new BorderLayout());

    ResourceBundleUtil bundle = ResourceBundleUtil.getBundle();
    String[] columns = {"Name",
                        bundle.getString("TransportOrdersContainerPanel.source"),
                        bundle.getString("TransportOrdersContainerPanel.target"),
                        bundle.getString("TransportOrdersContainerPanel.intendedVehicle"),
                        bundle.getString("TransportOrdersContainerPanel.executingVehicle"),
                        "Status",
                        bundle.getString("TransportOrdersContainerPanel.sequence")};
    fTableModel = new FilterTableModel(new DefaultTableModel(columns, 0));
    fTableModel.setColumnIndexToFilter(5); // Column "Status"
    fTable = new OrdersTable(fTableModel);
    fTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    JScrollPane scrollPane = new JScrollPane(fTable);
    add(scrollPane, BorderLayout.CENTER);

    fFilterButtons = createFilterButtons();
    JToolBar toolBar = createToolBar(fFilterButtons);
    addControlButtons(toolBar);
    add(toolBar, BorderLayout.NORTH);

    fTable.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getButton() == MouseEvent.BUTTON1) {
          if (evt.getClickCount() == 2) {
            showTransportOrder();
          }
        }

        if (evt.getButton() == MouseEvent.BUTTON3) {
          if (fTable.getSelectedRow() != -1) {
            showPopupMenu(evt.getX(), evt.getY());
          }
        }
      }
    });
  }

  /**
   * Zeigt Details zum ausgew�hlten Transportauftrag.
   */
  private void showTransportOrder() {
    try {
      TransportOrder transportOrder = getSelectedTransportOrder();

      if (transportOrder != null) {
        transportOrder = getKernel().getTCSObject(TransportOrder.class, transportOrder.getReference());
        DialogContent content = new TransportOrderView(transportOrder);
        StandardContentDialog dialog
            = new StandardContentDialog(JOptionPane.getFrameForComponent(this),
                                        content,
                                        true,
                                        StandardContentDialog.CLOSE);
        dialog.setTitle(ResourceBundleUtil.getBundle().getString("TransportOrdersContainerPanel.transportOrder"));
        dialog.setVisible(true);
      }
    }
    catch (CredentialsException e) {
    }
  }

  /**
   * Erzeugt auf der Vorlage eines existierenden Transportauftrags einen neuen
   * Transportauftrag, der aber noch bearbeitet werden kann.
   */
  private void createTransportOrderWithPattern() {
    TransportOrder to = getSelectedTransportOrder();
    if (to != null) {
      to = getKernel().getTCSObject(TransportOrder.class, to.getReference());
      CreateTransportOrderPanel content = orderPanelProvider.get();
      content.setPattern(to);
      StandardContentDialog dialog
          = new StandardContentDialog(JOptionPane.getFrameForComponent(this),
                                      content);
      dialog.setTitle(ResourceBundleUtil.getBundle().getString("TransportOrdersContainerPanel.newTransportOrder"));
      dialog.setVisible(true);

      if (dialog.getReturnStatus() == StandardContentDialog.RET_OK) {
        orderUtil.createTransportOrder(content.getLocations(), content.getActions(), content.getSelectedDeadline(), content.getSelectedVehicle());
      }
    }
  }

  /**
   * Fertigt eine Kopie eines existierenden Transportauftrags an.
   */
  private void createTransportOrderCopy() {
    orderUtil.createTransportOrder(getSelectedTransportOrder());
  }

  /**
   * Zeigt ein Kontextmen� zu dem angew�hlten Transportauftrag.
   *
   * @param x die x-Position des ausl�senden Mausklicks
   * @param y die y-Position des ausl�senden Mausklicks
   */
  private void showPopupMenu(int x, int y) {
    boolean singleRowSelected = fTable.getSelectedRowCount() <= 1;
    ResourceBundleUtil bundle = ResourceBundleUtil.getBundle();
    JPopupMenu menu = new JPopupMenu();
    JMenuItem item = menu.add(bundle.getString("TransportOrdersContainerPanel.popup.showDetails"));
    item.setEnabled(singleRowSelected);
    item.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent evt) {
        showTransportOrder();
      }
    });

    menu.add(new JSeparator());

    item = menu.add(bundle.getString("TransportOrdersContainerPanel.popup.asPattern"));
    item.setEnabled(singleRowSelected);
    item.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent evt) {
        createTransportOrderWithPattern();
      }
    });

    item = menu.add(bundle.getString("TransportOrdersContainerPanel.popup.copy"));
    item.setEnabled(singleRowSelected);
    item.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent evt) {
        createTransportOrderCopy();
      }
    });

    menu.show(fTable, x, y);
  }

  /**
   * F�gt die Buttons hinzu, mit denen die Transportauftr�ge gesteuert werden
   * k�nnen.
   */
  private void addControlButtons(JToolBar toolBar) {
    JButton button;
    IconToolkit iconkit = IconToolkit.instance();
    ResourceBundleUtil bundle = ResourceBundleUtil.getBundle();

    toolBar.add(new JToolBar.Separator());

    // 2012-11-19 HH: Funktion "Auftrag l�schen" im Client _nicht_ anbieten
//		button = new JButton(iconkit.getImageIconByFullPath(fIconPath + "delete.16x16.gif"));
//		button.addActionListener(new ActionListener() {
//
//			public void actionPerformed(ActionEvent e) {
//				deleteSelectedTransportOrders();
//			}
//		});
//
//		button.setToolTipText("L�scht die markierten Transportauftr�ge.");
//		toolBar.add(button);
    button = new JButton(iconkit.getImageIconByFullPath(fIconPath + "table-row-delete-2.16x16.png"));
    button.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        withdrawTransportOrder();
      }
    });

    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.withdrawTO"));
    toolBar.add(button);
  }

  /**
   * Liefert den selektierten Transportauftrag.
   *
   * @return den selektierten Transportauftrag oder    <code>null
   * </code>, wenn kein Transportauftrag selektiert wurde
   */
  private TransportOrder getSelectedTransportOrder() {
    int row = fTable.getSelectedRow();

    if (row == -1) {
      return null;
    }

    int index = fTableModel.realRowIndex(row);

    return fTransportOrders.elementAt(index);
  }

  /**
   * Setzt die Liste der Transportauftr�ge.
   */
  private void setTransportOrders(Set<TransportOrder> transportOrders) {
    if (transportOrders == null) {
      return;
    }

    fTransportOrders = new Vector<>();
    Iterator<TransportOrder> i = transportOrders.iterator();

    while (i.hasNext()) {
      TransportOrder t = i.next();
      fTransportOrders.addElement(t);
      fTableModel.addRow(toTableRow(t));
    }
  }

  /**
   * Deletes all transport orders, eg after changing the kernel state.
   */
  public void clearTransportOrders() {
    if (fTransportOrders != null) {
      if (fTableModel != null) {
        fTableModel.setRowCount(0);
      }
      fTransportOrders.clear();
    }
  }

  @Handler
  public void handleOrderEvent(TransportOrderEvent evt) {
    switch (evt.getType()) {
      case ORDER_CREATED:
        transportOrderAdded(evt.getOrder());
        break;
      case ORDER_CHANGED:
        transportOrderChanged(evt.getOrder());
        break;
      case ORDER_REMOVED:
        transportOrderRemoved(evt.getOrder());
        break;
      default:
      // Do nada.
    }
  }

  private void transportOrderAdded(TransportOrder t) {
    fTransportOrders.insertElementAt(t, 0);
    fTableModel.insertRow(0, toTableRow(t));
  }

  private void transportOrderChanged(TransportOrder t) {
    int rowIndex = fTransportOrders.indexOf(t);
    Vector<Object> values = toTableRow(t);

    for (int i = 0; i < values.size(); i++) {
      fTableModel.setValueAt(values.elementAt(i), rowIndex, i);
    }
  }

  private void transportOrderRemoved(final TransportOrder t) {
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        int i = fTransportOrders.indexOf(t);
        fTableModel.removeRow(i);
        fTransportOrders.removeElementAt(i);
      }
    });
  }

  /**
   * Erzeugt die Filterbuttons.
   */
  private Vector<FilterButton> createFilterButtons() {
    ResourceBundleUtil bundle = ResourceBundleUtil.getBundle();
    FilterButton button;
    Vector<FilterButton> buttons = new Vector<>();
    IconToolkit iconkit = IconToolkit.instance();
    // M�gliche States einer Transport Order:
    // RAW: A transport order's initial state. A transport order remains in this state until its parameters have been set up completely.
    button = new FilterButton(iconkit.getImageIconByFullPath(fIconPath + "filterRaw.16x16.gif"), fTableModel, "RAW");
    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.filterRAW"));
    buttons.add(button);
    // ACTIVE: Set (by a user/client) when a transport order's parameters have been set up completely and the kernel should dispatch it when possible.
    // UNROUTABLE: Failure state that marks a transport order as unroutable, i.e. it is impossible to find a route that would allow a vehicle to process the transport order completely.
    // DISPATCHABLE: Marks a transport order as ready to be dispatched to a vehicle (i.e. all its dependencies have been finished).
    button = new FilterButton(iconkit.getImageIconByFullPath(fIconPath + "filterActivated.16x16.gif"), fTableModel, "DISPATCHABLE");
    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.filterDISPATCHABLE"));
    buttons.add(button);
    // BEING_PROCESSED: Marks a transport order as being processed by a vehicle.
    button = new FilterButton(iconkit.getImageIconByFullPath(fIconPath + "filterProcessing.16x16.gif"), fTableModel, "BEING_PROCESSED");
    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.filterPROCESSED"));
    buttons.add(button);
    // FINISHED: Marks a transport order as successfully completed.
    button = new FilterButton(iconkit.getImageIconByFullPath(fIconPath + "filterFinished.16x16.gif"), fTableModel, "FINISHED");
    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.filterFINISHED"));
    buttons.add(button);
    // WITHDRAWN: Indicates the transport order is withdrawn from a processing vehicle but not yet in its final state (which will be FAILED), as the vehicle has not yet finished/cleaned up.
    // FAILED: General failure state that marks a transport order as failed.
    button = new FilterButton(iconkit.getImageIconByFullPath(fIconPath + "filterFailed.16x16.gif"), fTableModel, "FAILED");
    button.setToolTipText(bundle.getString("TransportOrdersContainerPanel.bar.filterFAILED"));
    buttons.add(button);

    return buttons;
  }

  /**
   * Initialisiert die Toolleiste.
   */
  private JToolBar createToolBar(List<FilterButton> filterButtons) {
    JToolBar toolBar = new JToolBar();

    for (FilterButton button : filterButtons) {
      toolBar.add(button);
    }

    return toolBar;
  }

  /**
   * Wandelt die Werte eines Transportauftrags in eine Tabellenzeile um.
   *
   * @param t der Transportauftrag
   */
  private Vector toTableRow(TransportOrder t) {
    Vector<String> row = new Vector<>();
    ResourceBundleUtil bundle = ResourceBundleUtil.getBundle();
    // Spalte 0: Name
    row.addElement(t.getName());

    // Alle Einzel-Fahrauftr�ge
    Vector<DriveOrder> driveOrders = new Vector<>();
    driveOrders.addAll(t.getPastDriveOrders());

    if (t.getCurrentDriveOrder() != null) {
      driveOrders.addElement(t.getCurrentDriveOrder());
    }

    driveOrders.addAll(t.getFutureDriveOrders());
    TCSObjectReference ref = null;

    // Spalte 1: Quelle
    if (driveOrders.size() == 1) {
      row.addElement("");
    }
    else {
      ref = driveOrders.firstElement().getDestination().getLocation();
      row.addElement(ref.getName());
    }

    // Spalte 2: Ziel
    ref = driveOrders.lastElement().getDestination().getLocation();
    row.addElement(ref.getName());

    // Spalte 3: Gew�nschtes Fahrzeug
    ref = t.getIntendedVehicle();

    if (ref != null) {
      row.addElement(ref.getName());
    }
    else {
      row.addElement(bundle.getString("TransportOrdersContainerPanel.table.determineAutomatic"));
    }

    // Spalte 4: Ausf�hrendes Fahrzeug
    ref = t.getProcessingVehicle();

    if (ref != null) {
      row.addElement(ref.getName());
    }
    else {
      row.addElement("?");
    }

    // Spalte 5: Status
    row.addElement(t.getState().toString());

    // Spalte 6: Order Sequence
    TCSObjectReference<OrderSequence> wrappingSequence = t.getWrappingSequence();

    if (wrappingSequence != null) {
      row.addElement(wrappingSequence.getName());
    }
    else {
      row.addElement("-");
    }

    return row;
  }

  private void withdrawTransportOrder() {
    int[] indices = fTable.getSelectedRows();
    Vector<TransportOrder> toWithdraw = new Vector<>();

    for (int i = 0; i < indices.length; i++) {
      int realIndex = fTableModel.realRowIndex(indices[i]);
      TransportOrder order = fTransportOrders.elementAt(realIndex);
      toWithdraw.insertElementAt(order, 0);
    }

    Enumeration<TransportOrder> e = toWithdraw.elements();

    while (e.hasMoreElements()) {
      TransportOrder order = e.nextElement();
      getKernel().withdrawTransportOrder(order.getReference(), false);
      // TODO: Soll es auch eine Option geben, den Auftrag zu l�schen und das Fzg danach zu sperren?
//		getKernel().withdrawTransportOrder(order.getReference(), true);
    }
  }
}
