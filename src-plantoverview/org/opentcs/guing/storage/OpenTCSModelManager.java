/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.storage;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JFileChooser;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.Figure;
import org.opentcs.access.CredentialsException;
import org.opentcs.access.Kernel;
import org.opentcs.access.KernelRuntimeException;
import org.opentcs.data.ObjectPropConstants;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.model.Block;
import org.opentcs.data.model.Group;
import org.opentcs.data.model.Location;
import org.opentcs.data.model.Location.Link;
import org.opentcs.data.model.LocationType;
import org.opentcs.data.model.Path;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.StaticRoute;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.model.visualization.ElementPropKeys;
import org.opentcs.data.model.visualization.LocationRepresentation;
import org.opentcs.data.model.visualization.ModelLayoutElement;
import org.opentcs.data.model.visualization.VisualLayout;
import org.opentcs.guing.application.StatusPanel;
import org.opentcs.guing.components.drawing.course.Origin;
import org.opentcs.guing.components.drawing.figures.FigureConstants;
import org.opentcs.guing.components.drawing.figures.LabeledLocationFigure;
import org.opentcs.guing.components.drawing.figures.LabeledPointFigure;
import org.opentcs.guing.components.drawing.figures.LinkConnection;
import org.opentcs.guing.components.drawing.figures.LocationFigure;
import org.opentcs.guing.components.drawing.figures.PathConnection;
import org.opentcs.guing.components.drawing.figures.PointFigure;
import org.opentcs.guing.components.drawing.figures.TCSLabelFigure;
import org.opentcs.guing.components.properties.event.NullAttributesChangeListener;
import org.opentcs.guing.components.properties.type.ColorProperty;
import org.opentcs.guing.components.properties.type.KeyValueProperty;
import org.opentcs.guing.components.properties.type.KeyValueSetProperty;
import org.opentcs.guing.components.properties.type.LengthProperty;
import org.opentcs.guing.components.properties.type.LocationTypeProperty;
import org.opentcs.guing.components.properties.type.SelectionProperty;
import org.opentcs.guing.components.properties.type.StringProperty;
import org.opentcs.guing.components.properties.type.StringSetProperty;
import org.opentcs.guing.components.properties.type.SymbolProperty;
import org.opentcs.guing.exchange.adapter.BlockAdapter;
import org.opentcs.guing.exchange.adapter.GroupAdapter;
import org.opentcs.guing.exchange.adapter.LayoutAdapter;
import org.opentcs.guing.exchange.adapter.LinkAdapter;
import org.opentcs.guing.exchange.adapter.LocationAdapter;
import org.opentcs.guing.exchange.adapter.LocationTypeAdapter;
import org.opentcs.guing.exchange.adapter.PathAdapter;
import org.opentcs.guing.exchange.adapter.PointAdapter;
import org.opentcs.guing.exchange.adapter.ProcessAdapter;
import org.opentcs.guing.exchange.adapter.ProcessAdapterFactory;
import org.opentcs.guing.exchange.adapter.ProcessAdapterUtil;
import org.opentcs.guing.exchange.adapter.StaticRouteAdapter;
import org.opentcs.guing.exchange.adapter.VehicleAdapter;
import org.opentcs.guing.model.ModelComponent;
import org.opentcs.guing.model.ModelManager;
import org.opentcs.guing.model.SystemModel;
import org.opentcs.guing.model.elements.AbstractConnection;
import org.opentcs.guing.model.elements.BlockModel;
import org.opentcs.guing.model.elements.GroupModel;
import org.opentcs.guing.model.elements.LayoutModel;
import org.opentcs.guing.model.elements.LinkModel;
import org.opentcs.guing.model.elements.LocationModel;
import org.opentcs.guing.model.elements.LocationTypeModel;
import org.opentcs.guing.model.elements.PathModel;
import org.opentcs.guing.model.elements.PointModel;
import org.opentcs.guing.model.elements.StaticRouteModel;
import org.opentcs.guing.model.elements.VehicleModel;
import org.opentcs.guing.util.Colors;
import org.opentcs.guing.util.CourseObjectFactory;
import org.opentcs.guing.util.ResourceBundleUtil;
import org.opentcs.util.ObjectListCycler;

/**
 * Manages (loads, persists and keeps) the driving course model.
 *
 * @author Sebastian Naumann (ifak e.V. Magdeburg)
 * @author Stefan Walter (Fraunhofer IML)
 */
public class OpenTCSModelManager
    implements ModelManager {

  /**
   * Identifier f�r das Layout-Objekt, welches das standardm��ige Fahrkursmodell
   * enth�lt.
   */
  public static final String DEFAULT_LAYOUT = "Default";
  /**
   * Directory where models will be persisted.
   */
  public static final String MODEL_DIRECTORY = "data/";
  /**
   * File ending of locally saved models.
   */
  public static final String FILE_ENDING = ".opentcs";
  /**
   * This class's logger.
   */
  private static final Logger log
      = Logger.getLogger(OpenTCSModelManager.class.getName());
  /**
   * The StatusPanel at the bottom to log messages.
   */
  private final StatusPanel statusPanel;
  /**
   * A course object factory to be used.
   */
  private final CourseObjectFactory crsObjFactory;
  /**
   * Provides new instances of SystemModel.
   */
  private final Provider<SystemModel> systemModelProvider;
  /**
   * A factory for process adapters.
   */
  private final ProcessAdapterFactory procAdapterFactory;
  /**
   * A utility class for process adapters.
   */
  private final ProcessAdapterUtil procAdapterUtil;
  /**
   * The model currently loaded.
   */
  private SystemModel systemModel;
  /**
   * The current system model's name.
   */
  private String fModelName = "";

  /**
   * Creates a new instance.
   *
   * @param crsObjFactory A course object factory to be used.
   * @param procAdapterFactory A process adapter factory to be used.
   * @param procAdapterUtil A utility class for process adapters.
   * @param systemModelProvider Provides instances of SystemModel.
   * @param statusPanel StatusPanel to log messages.
   */
  @Inject
  public OpenTCSModelManager(CourseObjectFactory crsObjFactory,
                             ProcessAdapterFactory procAdapterFactory,
                             ProcessAdapterUtil procAdapterUtil,
                             Provider<SystemModel> systemModelProvider,
                             StatusPanel statusPanel) {
    this.crsObjFactory = requireNonNull(crsObjFactory, "crsObjFactory");
    this.procAdapterFactory = requireNonNull(procAdapterFactory,
                                             "procAdapterFactory");
    this.procAdapterUtil = requireNonNull(procAdapterUtil, "procAdapterUtil");
    this.systemModelProvider = requireNonNull(systemModelProvider,
                                              "systemModelProvider");
    this.systemModel = systemModelProvider.get();
    this.statusPanel = requireNonNull(statusPanel, "statusPanel");
  }

  @Override
  public SystemModel getModel() {
    return systemModel;
  }

  @Override
  public boolean loadModel(@Nullable File modelFile) {
    File file = modelFile != null ? modelFile : showOpenDialog();
    if (file != null) {
      SystemModel modelToLoad = systemModelProvider.get();
      ModelJAXBReader reader = new ModelJAXBReader(modelToLoad);
      try {
        systemModel = reader.deserialize(file);
        statusPanel.clear();
        return true;
      }
      catch (IOException | IllegalArgumentException ex) {
        statusPanel.setLogMessage(Level.SEVERE,
                                  ResourceBundleUtil.getBundle()
                                  .getFormatted("modelManager.persistence.notLoaded",
                                                file.getName()));
        log.log(Level.INFO, "Error reading file", ex);
      }
    }

    return false;
  }

  @Override
  public boolean persistModel(Kernel kernel) {
    try {
      fModelName = systemModel.getName();
      ModelPersistor persistor = new ModelKernelPersistor(
          systemModel.getEventDispatcher(), kernel, fModelName);
      statusPanel.clear();
      return persistModel(systemModel, persistor);
    }
    catch (IOException | CredentialsException e) {
      statusPanel.setLogMessage(Level.SEVERE,
                                ResourceBundleUtil.getBundle().getString("modelManager.persistence.notSaved"));
      log.log(Level.WARNING, "Exception persisting model", e);
      return false;
    }
  }

  @Override
  public boolean persistModel(boolean chooseName) {
    fModelName = systemModel.getName();
    if (chooseName
        || fModelName == null
        || fModelName.isEmpty()
        || fModelName.equals(Kernel.DEFAULT_MODEL_NAME)
        || fModelName.equals(
            ResourceBundleUtil.getBundle().getString("file.newModel.text"))) {
      if (!showSaveDialog()) {
        return false;
      }
    }
    File dataDir
        = new File(System.getProperty("user.dir") + File.separator + MODEL_DIRECTORY);
    if (!dataDir.isDirectory()) {
      dataDir.mkdir();
    }
    File file = new File(System.getProperty("user.dir")
        + File.separator
        + MODEL_DIRECTORY
        + fModelName + FILE_ENDING);
    try {
      ModelJAXBPersistor persistor = new ModelJAXBPersistor(file);
      return persistModel(systemModel, persistor);
    }
    catch (IOException e) {
      statusPanel.setLogMessage(Level.SEVERE,
                                ResourceBundleUtil.getBundle().getString("modelManager.persistence.notSaved"));
      log.log(Level.WARNING, "Exception persisting model", e);
      return false;
    }
  }

  @Override
  public void createEmptyModel() {
    systemModel = systemModelProvider.get();
    List<LayoutModel> layoutModels = systemModel.getLayoutModels();
    if (!layoutModels.isEmpty()) {
      double scaleX = Origin.DEFAULT_SCALE;
      double scaleY = Origin.DEFAULT_SCALE;
      LayoutModel layoutModel = layoutModels.get(0);
      LengthProperty pScaleX = (LengthProperty) layoutModel.getProperty(LayoutModel.SCALE_X);
      if (pScaleX.getValueByUnit(LengthProperty.Unit.MM) == 0) {
        pScaleX.setValueAndUnit(scaleX, LengthProperty.Unit.MM);
      }
      LengthProperty pScaleY = (LengthProperty) layoutModel.getProperty(LayoutModel.SCALE_Y);
      if (pScaleY.getValueByUnit(LengthProperty.Unit.MM) == 0) {
        pScaleY.setValueAndUnit(scaleY, LengthProperty.Unit.MM);
      }
    }
    systemModel.setName(Kernel.DEFAULT_MODEL_NAME);
    fModelName = systemModel.getName();
  }

  /**
   * Persist model with the persistor.
   *
   * @param systemModel The system model to be persisted.
   * @param persistor The persistor to be used.
   * @return Whether the model was actually saved.
   */
  private boolean persistModel(SystemModel systemModel, ModelPersistor persistor)
      throws IOException, KernelRuntimeException {
    requireNonNull(systemModel, "systemModel");
    requireNonNull(persistor, "persistor");

    persistor.init();

    for (LayoutModel model : systemModel.getLayoutModels()) {
      persistor.persist(model);
    }
    for (PointModel model : systemModel.getPointModels()) {
      persistor.persist(model);
    }
    for (PathModel model : systemModel.getPathModels()) {
      // XXX PathAdapter needs to be changed: connectionChanged() should not do
      // anything while modelling. establishPath() should only be called in
      // updateProcessProperties(), or even better, moved to createProcessObject().
      // XXX Registering/Unregistering of ConnectionChangeListener in PathAdapter still correct?
      persistor.persist(model);
    }
    for (LocationTypeModel model : systemModel.getLocationTypeModels()) {
      persistor.persist(model);
    }
    for (LocationModel model : systemModel.getLocationModels()) {
      persistor.persist(model);
    }
    for (LinkModel model : systemModel.getLinkModels()) {
      // XXX LinkAdapter needs to be changed: connectionChanged() should not do
      // anything while modelling. establishLink() should only be called in
      // updateProcessProperties(), or even better, moved to createProcessObject().
      // XXX Registering/Unregistering of ConnectionChangeListener in LinkAdapter still correct?
      persistor.persist(model);
    }
    for (BlockModel model : systemModel.getBlockModels()) {
      persistor.persist(model);
    }
    for (GroupModel model : systemModel.getGroupModels()) {
      persistor.persist(model);
    }
    for (StaticRouteModel model : systemModel.getStaticRouteModels()) {
      persistor.persist(model);
    }
    for (VehicleModel model : systemModel.getVehicleModels()) {
      persistor.persist(model);
    }

    persistor.close();
    systemModel.setName(fModelName);

    return true;
  }

  @Override
  public void restoreModel() {
    fModelName = systemModel.getName();
    List<Figure> restoredFigures = new ArrayList<>();

    Origin origin = systemModel.getDrawingMethod().getOrigin();
    LayoutModel layoutComponent
        = (LayoutModel) systemModel.getMainFolder(SystemModel.FolderKey.LAYOUT);
    LengthProperty scale = (LengthProperty) layoutComponent.getProperty(LayoutModel.SCALE_X);
    double scaleX = scale.getValueByUnit(LengthProperty.Unit.MM);
    scale = (LengthProperty) layoutComponent.getProperty(LayoutModel.SCALE_Y);
    double scaleY = scale.getValueByUnit(LengthProperty.Unit.MM);

    restoreModelPoints(systemModel.getPointModels(), restoredFigures, origin, scaleX, scaleY);
    restoreModelPaths(systemModel.getPathModels(), restoredFigures, origin);
    restoreModelLocations(systemModel.getLocationModels(), restoredFigures, origin, scaleX, scaleY);
    restoreModelLocationTypes(systemModel.getLocationTypeModels());
    restoreModelBlocks(systemModel.getBlockModels());
    restoreModelStaticRoutes(systemModel.getStaticRouteModels());
    restoreModelGroups(systemModel.getGroupModels());
    restoreModelVehicles(systemModel.getVehicleModels());
    restoreModelLinks(systemModel.getLinkModels());

    Drawing drawing = systemModel.getDrawing();

    restoredFigures.stream().forEach((figure) -> {
      drawing.add(figure);
    });
  }

  @Override
  public void restoreModel(Kernel kernel) {
    createEmptyModel();
    fModelName = kernel.getCurrentModelName();
    ((StringProperty) systemModel.getProperty(ModelComponent.NAME)).setText(fModelName);

    // Die im Kernel gespeicherten Layouts
    Set<VisualLayout> allVisualLayouts = kernel.getTCSObjects(VisualLayout.class);
    Set<Vehicle> allVehicles = kernel.getTCSObjects(Vehicle.class);
    Set<Point> allPoints = kernel.getTCSObjects(Point.class);
    Set<LocationType> allLocationTypes = kernel.getTCSObjects(LocationType.class);
    Set<Location> allLocations = kernel.getTCSObjects(Location.class);
    Set<Path> allPaths = kernel.getTCSObjects(Path.class);
    Set<Block> allBlocks = kernel.getTCSObjects(Block.class);
    Set<StaticRoute> allStaticRoutes = kernel.getTCSObjects(StaticRoute.class);
    Set<Group> allGroups = kernel.getTCSObjects(Group.class);

    Set<ProcessAdapter> createdAdapters = new HashSet<>();
    List<Figure> restoredFigures = new ArrayList<>();

    double scaleX = Origin.DEFAULT_SCALE;
    double scaleY = Origin.DEFAULT_SCALE;
    Origin origin = systemModel.getDrawingMethod().getOrigin();

    // "Neues" Modell: Layout ist im Visual-Layout Objekt gespeichert
    VisualLayout visualLayout = null;
    for (VisualLayout visLayout : allVisualLayouts) {
      visualLayout = visLayout;	// Es sollte genau ein Layout geben
      systemModel.createLayoutMap(visualLayout, allPoints, allPaths, allLocations, allBlocks);
      scaleX = visualLayout.getScaleX();
      scaleY = visualLayout.getScaleY();

      if (scaleX != 0.0 && scaleY != 0.0) {
        origin.setScale(scaleX, scaleY);
      }
    }

    LayoutModel layoutComponent
        = (LayoutModel) systemModel.getMainFolder(SystemModel.FolderKey.LAYOUT);
    // Leeres Modell: Default-Layout erzeugen
    LayoutAdapter adapter;
    if (visualLayout == null) {
      StringProperty name = (StringProperty) layoutComponent.getProperty(LayoutModel.NAME);
      name.setText("VLayout");

      try {
        LengthProperty scale = (LengthProperty) layoutComponent.getProperty(LayoutModel.SCALE_X);
        scale.setValueAndUnit(scaleX, LengthProperty.Unit.MM);
        scale = (LengthProperty) layoutComponent.getProperty(LayoutModel.SCALE_Y);
        scale.setValueAndUnit(scaleY, LengthProperty.Unit.MM);
      }
      catch (IllegalArgumentException ex) {
        log.log(Level.WARNING, "Exception in setValueAndUnit():\n{0}", ex);
      }

      adapter = createLayoutAdapter(systemModel, layoutComponent);
      createdAdapters.add(adapter);
    }
    else {
      // TODO: Bei mehreren Layouts muss jedem SystemFolder der Name des Layouts zugewiesen werden
      adapter = procAdapterFactory.createLayoutAdapter(
          layoutComponent, systemModel.getEventDispatcher());
      adapter.register();

      adapter.updateModelProperties(kernel,
                                    visualLayout,
                                    null);
    }

    restoreModelPoints(allPoints, systemModel, origin, scaleX, scaleY, createdAdapters, restoredFigures, kernel);
    restoreModelPaths(allPaths, systemModel, origin, createdAdapters, restoredFigures, kernel);
    restoreModelVehicles(allVehicles, systemModel, createdAdapters, kernel);
    restoreModelLocationTypes(allLocationTypes, systemModel, createdAdapters, kernel);
    restoreModelLocations(allLocations, systemModel, origin, scaleX, scaleY, createdAdapters, restoredFigures, kernel);
    restoreModelBlocks(allBlocks, systemModel, createdAdapters, kernel);
    restoreModelStaticRoutes(allStaticRoutes, systemModel, createdAdapters, kernel);
    restoreModelGroups(allGroups, systemModel, createdAdapters, kernel);

    Drawing drawing = systemModel.getDrawing();

    for (Figure figure : restoredFigures) {
      drawing.add(figure);
    }
  }

  private void restoreModelGroups(List<GroupModel> groupModels) {
    for (GroupModel groupModel : groupModels) {
      StringSetProperty pElements
          = (StringSetProperty) groupModel.getProperty(GroupModel.ELEMENTS);
      for (String elementName : pElements.getItems()) {
        ModelComponent modelComponent = getGroupMember(elementName);
        if (modelComponent != null) {
          groupModel.add(modelComponent);
          procAdapterUtil.createProcessAdapter(modelComponent,
                                               systemModel
                                               .getEventDispatcher());
        }
      }
    }
  }

  private void restoreModelGroups(Set<Group> allGroups, SystemModel systemModel,
                                  Set<ProcessAdapter> createdAdapters,
                                  Kernel kernel) {
    // --- Groups ---
    for (Group group : allGroups) {
      GroupModel groupModel = new GroupModel(group.getName());
      GroupAdapter adapter = procAdapterFactory.createGroupAdapter(
          groupModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      adapter.updateModelProperties(kernel,
                                    group,
                                    null);

      systemModel.getMainFolder(SystemModel.FolderKey.GROUPS).add(groupModel);
      Set<TCSObjectReference<?>> refs = group.getMembers();

      for (TCSObjectReference ref : refs) {
        if (ref.getReferentClass() == Point.class) {
          Point point = kernel.getTCSObject(Point.class, ref);

          if (point != null) {
            groupModel.add(systemModel.getPointModel(point.getName()));
          }
        }
        else if (ref.getReferentClass() == Location.class) {
          Location location = kernel.getTCSObject(Location.class, ref);

          if (location != null) {
            groupModel.add(systemModel.getLocationModel(location.getName()));
          }
        }
        else if (ref.getReferentClass() == Path.class) {
          Path path = kernel.getTCSObject(Path.class, ref);

          if (path != null) {
            groupModel.add(systemModel.getPathModel(path.getName()));
          }
        }
      }
    }
  }

  private void restoreModelStaticRoutes(List<StaticRouteModel> staticRouteModels) {
    for (StaticRouteModel staticRouteModel : staticRouteModels) {
      StringSetProperty pElements
          = (StringSetProperty) staticRouteModel.getProperty(StaticRouteModel.ELEMENTS);
      for (String elementName : pElements.getItems()) {
        PointModel modelComponent = getPointComponent(elementName);
        if (modelComponent != null) {
          staticRouteModel.addPoint(modelComponent);          
        }
      }
      procAdapterUtil.createProcessAdapter(staticRouteModel,
                                               systemModel
                                               .getEventDispatcher());
    }
  }

  private void restoreModelStaticRoutes(Set<StaticRoute> allStaticRoutes,
                                        SystemModel systemModel,
                                        Set<ProcessAdapter> createdAdapters,
                                        Kernel kernel) {
    // --- Static Routes ---
    ObjectListCycler<Color> routeColorCycler
        = new ObjectListCycler<>(Colors.defaultColors());
    for (StaticRoute staticRoute : allStaticRoutes) {
      StaticRouteModel staticRouteModel = crsObjFactory.createStaticRouteModel();
      StaticRouteAdapter adapter = procAdapterFactory.createStaticRouteAdapter(
          staticRouteModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      adapter.updateModelProperties(kernel,
                                    staticRoute,
                                    null);

      // Neue Farbe suchen f�r StaticRoutes, die min. 1 Hop haben
      if (!staticRoute.getHops().isEmpty()) {
        ((ColorProperty) staticRouteModel
         .getProperty(ElementPropKeys.BLOCK_COLOR))
            .setColor(routeColorCycler.next());
      }
      // Das zugeh�rige Model Layout Element suchen
      ModelLayoutElement element = systemModel.getLayoutMap().get(staticRoute.getReference());

      if (element != null) {
        Map<String, String> properties = element.getProperties();
        // Im Layout Element gespeicherte Farbe �berschreibt den Default-Wert
        String sColor = properties.get(ElementPropKeys.BLOCK_COLOR);
        String srgb = sColor.substring(1);	// delete trailing "#"
        int rgb = Integer.parseInt(srgb, 16);
        Color color = new Color(rgb);
        ColorProperty cp = (ColorProperty) staticRouteModel.getProperty(ElementPropKeys.BLOCK_COLOR);
        cp.setColor(color);
      }

      systemModel.getMainFolder(SystemModel.FolderKey.STATIC_ROUTES).add(staticRouteModel);
    }
  }

  private void restoreModelBlocks(List<BlockModel> blockModels) {
    for (BlockModel blockModel : blockModels) {
      StringSetProperty pElements = (StringSetProperty) blockModel.getProperty(BlockModel.ELEMENTS);
      for (String elementName : pElements.getItems()) {
        ModelComponent modelComponent = getBlockMember(elementName);
        if (modelComponent != null) {
          blockModel.addCourseElement(modelComponent);          
        }
      }
      procAdapterUtil.createProcessAdapter(blockModel,
                                               systemModel
                                               .getEventDispatcher());
    }
  }

  private void restoreModelBlocks(Set<Block> allBlocks, SystemModel systemModel,
                                  Set<ProcessAdapter> createdAdapters,
                                  Kernel kernel) {
    // --- Alle Blocks, die der Kernel kennt ---
    ObjectListCycler<Color> blockColorCycler
        = new ObjectListCycler<>(Colors.defaultColors());
    for (Block block : allBlocks) {
      BlockModel blockModel = crsObjFactory.createBlockModel();
      BlockAdapter adapter = procAdapterFactory.createBlockAdapter(
          blockModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      adapter.updateModelProperties(kernel,
                                    block,
                                    null);

//			Iterator iMembers = block.getMembers().iterator();
//
//			while (iMembers.hasNext()) {
//				ModelComponent modelComponent = getModelComponent(systemModel, (TCSObjectReference) iMembers.next());
//				blockModel.addCourseElement(modelComponent);
//			}
      // Neue Farbe suchen f�r Blocks, die mindestens ein Member haben
      if (!block.getMembers().isEmpty()) {
        ((ColorProperty) blockModel
         .getProperty(ElementPropKeys.BLOCK_COLOR))
            .setColor(blockColorCycler.next());
      }
      // Das zugeh�rige Model Layout Element suchen
      ModelLayoutElement element = systemModel.getLayoutMap().get(block.getReference());

      if (element != null) {
        Map<String, String> properties = element.getProperties();
        // Im Layout Element gespeicherte Farbe �berschreibt den Default-Wert
        String sColor = properties.get(ElementPropKeys.BLOCK_COLOR);
        String srgb = sColor.substring(1);	// delete trailing "#"
        Color color = new Color(Integer.parseInt(srgb, 16));
        ((ColorProperty) blockModel.getProperty(ElementPropKeys.BLOCK_COLOR))
            .setColor(color);
      }

      systemModel.getMainFolder(SystemModel.FolderKey.BLOCKS).add(blockModel);
    }
  }

  private void restoreModelLocations(List<LocationModel> locationModels,
                                     List<Figure> restoredFigures,
                                     Origin origin, double scaleX, double scaleY) {
    for (LocationModel locationModel : locationModels) {
      LabeledLocationFigure llf = crsObjFactory.createLocationFigure();
      LocationFigure locationFigure = llf.getPresentationFigure();
      locationFigure.set(FigureConstants.MODEL, locationModel);

      // The corresponding label
      TCSLabelFigure label = new TCSLabelFigure(locationModel.getName());

      Point2D.Double labelPosition;
      Point2D.Double figurePosition;
      double figurePositionX = 0;
      double figurePositionY = 0;
      StringProperty stringProperty = (StringProperty) locationModel.getProperty(ElementPropKeys.LOC_POS_X);
      String locPosX = stringProperty.getText();
      stringProperty = (StringProperty) locationModel.getProperty(ElementPropKeys.LOC_POS_Y);
      String locPosY = stringProperty.getText();
      if (locPosX != null && locPosY != null) {
        try {
          figurePositionX = Integer.parseInt(locPosX);
          figurePositionY = Integer.parseInt(locPosY);
        }
        catch (NumberFormatException ex) {
        }
      }

      // Label
      stringProperty = (StringProperty) locationModel.getProperty(ElementPropKeys.LOC_LABEL_OFFSET_X);
      String labelOffsetX = stringProperty.getText();
      stringProperty = (StringProperty) locationModel.getProperty(ElementPropKeys.LOC_LABEL_OFFSET_Y);
      String labelOffsetY = stringProperty.getText();
      // TODO: labelOrientationAngle auswerten
//			String labelOrientationAngle = layoutProperties.get(ElementPropKeys.POINT_LABEL_ORIENTATION_ANGLE);

      double labelPositionX;
      double labelPositionY;
      if (labelOffsetX != null && labelOffsetY != null) {
        try {
          labelPositionX = Integer.parseInt(labelOffsetX);
          labelPositionY = Integer.parseInt(labelOffsetY);
        }
        catch (NumberFormatException ex) {
          // XXX This does not look right.
          labelPositionX = labelPositionY = -20;
        }

        labelPosition = new Point2D.Double(labelPositionX, labelPositionY);
        label.setOffset(labelPosition);
      }
      figurePosition = new Point2D.Double(figurePositionX / scaleX, -figurePositionY / scaleY);	// Vorzeichen!
      locationFigure.setBounds(figurePosition, figurePosition);

      labelPosition = locationFigure.getStartPoint();
      labelPosition.x += label.getOffset().x;
      labelPosition.y += label.getOffset().y;
      label.setBounds(labelPosition, null);
      llf.setLabel(label);

      locationModel.setFigure(llf);
      locationModel.addAttributesChangeListener(llf);

      String locationTypeName
          = (String) ((LocationTypeProperty) locationModel.getProperty(LocationModel.TYPE)).getValue();
      locationModel.setLocationType(getLocationTypeComponent(locationTypeName));

      for (LinkModel linkModel : getAttachedLinks(locationModel)) {
        PointModel pointModel = linkModel.getPoint();
        LabeledPointFigure lpf = pointModel.getFigure();
        LinkConnection linkConnection = crsObjFactory.createLinkConnection();
        linkConnection.set(FigureConstants.MODEL, linkModel);
        linkConnection.connect(lpf, llf);

        linkModel.setFigure(linkConnection);
        linkModel.addAttributesChangeListener(linkConnection);
        restoredFigures.add(linkConnection);
      }

      procAdapterUtil.createProcessAdapter(locationModel,
                                           systemModel
                                           .getEventDispatcher());
      locationModel.propertiesChanged(new NullAttributesChangeListener());
      origin.addListener(llf);
      llf.set(FigureConstants.ORIGIN, origin);
      restoredFigures.add(llf);
    }
  }

  private void restoreModelLocations(Set<Location> allLocations,
                                     SystemModel systemModel,
                                     Origin origin, double scaleX, double scaleY,
                                     Set<ProcessAdapter> createdAdapters,
                                     List<Figure> restoredFigures,
                                     Kernel kernel) {
    // --- Alle Locations, die der Kernel kennt ---
    for (Location location : allLocations) {
      // Neues Figure-Objekt
      LabeledLocationFigure llf = crsObjFactory.createLocationFigure();
      LocationFigure locationFigure = llf.getPresentationFigure();
      // Das zugeh�rige Modell
      LocationModel locationModel = locationFigure.getModel();
      // Adapter zur Verkn�pfung des Kernel-Objekts mit der Figur
      LocationAdapter adapter = procAdapterFactory.createLocationAdapter(
          locationModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      // Das zugeh�rige Model Layout Element suchen und mit dem Adapter verkn�pfen
      ModelLayoutElement layoutElement
          = systemModel.getLayoutMap().get(location.getReference());

      // Setze Typ, Koordinaten, ... aus dem Kernel-Modell
      adapter.updateModelProperties(kernel,
                                    location,
                                    layoutElement);
      // Default-Position f�r den Fall, dass kein Layout-Element zu dieser Location gefunden wurde
      double figurePositionX = location.getPosition().getX();
      double figurePositionY = location.getPosition().getY();
      // Die zugeh�rige Beschriftung:
      TCSLabelFigure label = new TCSLabelFigure(location.getName());

      Point2D.Double labelPosition;
      if (layoutElement != null) {
        Map<String, String> layoutProperties = layoutElement.getProperties();
        String locPosX = layoutProperties.get(ElementPropKeys.LOC_POS_X);
        String locPosY = layoutProperties.get(ElementPropKeys.LOC_POS_Y);
        // Die in den Properties gespeicherte Position �berschreibt die im Kernel-Objekt gespeicherten Werte
        // TO DO: Auswahl, z.B. �ber Parameter?
        if (locPosX != null && locPosY != null) {
          try {
            figurePositionX = Integer.parseInt(locPosX);
            figurePositionY = Integer.parseInt(locPosY);
          }
          catch (NumberFormatException ex) {
            figurePositionX = figurePositionY = 0;
          }
        }

        String labelOffsetX = layoutProperties.get(ElementPropKeys.LOC_LABEL_OFFSET_X);
        String labelOffsetY = layoutProperties.get(ElementPropKeys.LOC_LABEL_OFFSET_Y);
        // TODO: labelOrientationAngle auswerten
//			String labelOrientationAngle = properties.get(ElementPropKeys.LOC_LABEL_ORIENTATION_ANGLE);

        double labelPositionX;
        double labelPositionY;
        if (labelOffsetX != null && labelOffsetY != null) {
          try {
            labelPositionX = Integer.parseInt(labelOffsetX);
            labelPositionY = Integer.parseInt(labelOffsetY);
          }
          catch (NumberFormatException ex) {
            labelPositionX = labelPositionY = -20;
          }

          labelPosition = new Point2D.Double(labelPositionX, labelPositionY);
          label.setOffset(labelPosition);
        }
      }
      // Figur auf diese Position verschieben
      Point2D.Double figurePosition = new Point2D.Double(figurePositionX / scaleX, -figurePositionY / scaleY);	// Vorzeichen!
      locationFigure.setBounds(figurePosition, figurePosition);

      labelPosition = locationFigure.getStartPoint();
      labelPosition.x += label.getOffset().x;
      labelPosition.y += label.getOffset().y;
      label.setBounds(labelPosition, labelPosition);
      llf.setLabel(label);

      locationModel.setFigure(llf);
      locationModel.addAttributesChangeListener(llf);
      systemModel.getMainFolder(SystemModel.FolderKey.LOCATIONS).add(locationModel);
      restoredFigures.add(llf);
      // Den Stationstyp zuweisen
      // Der Typ der Station
      LocationTypeModel type = (LocationTypeModel) getModelComponent(systemModel, location.getType());
      locationModel.setLocationType(type);
      locationModel.updateTypeProperty(systemModel.getLocationTypeModels());
      locationModel.propertiesChanged(new NullAttributesChangeListener());
      // XXX Why clearing the objects properties? pseifert @ 25.04.14
      //kernel().clearTCSObjectProperties(location.getReference());
      KeyValueSetProperty misc = (KeyValueSetProperty) locationModel.getProperty(ModelComponent.MISCELLANEOUS);

      if (misc != null) {
        for (String key : location.getProperties().keySet()) {
          misc.addItem(new KeyValueProperty(locationModel, key, location.getProperties().get(key)));
        }
        // Datei f�r Default-Symbol
        SymbolProperty symbol = (SymbolProperty) locationModel.getProperty(ObjectPropConstants.LOC_DEFAULT_REPRESENTATION);

        if (symbol.getLocationRepresentation() != null) {
          LocationRepresentation symbolName = symbol.getLocationRepresentation();
          KeyValueProperty pr = new KeyValueProperty(locationModel, ObjectPropConstants.LOC_DEFAULT_REPRESENTATION, symbolName.name());
          misc.addItem(pr);

          Iterator<KeyValueProperty> e = misc.getItems().iterator();

          while (e.hasNext()) {
            pr = e.next();
            kernel.setTCSObjectProperty(location.getReference(), pr.getKey(), pr.getValue());
          }
        }
      }

      // Die zugeh�rigen Links
      for (Link link : location.getAttachedLinks()) {
        // Der mit dem Link verbundene Point
        PointModel pointModel = (PointModel) getModelComponent(systemModel, link.getPoint());
        LabeledPointFigure lpf = pointModel.getFigure();
        // Eine Figure zur Darstellung des Links...
        LinkConnection linkConnection = crsObjFactory.createLinkConnection();
        // ...verbindet Point und Location
        linkConnection.connect(lpf, llf);

        // Das zur Figure geh�rige Datenmodell in der GUI
        LinkModel linkModel = linkConnection.getModel();
        // Speziell f�r diesen Link erlaubte Operation
        StringSetProperty pOperations = (StringSetProperty) linkModel.getProperty(LinkModel.ALLOWED_OPERATIONS);
        pOperations.setItems(new ArrayList<>(link.getAllowedOperations()));

        LinkAdapter linkAdapter = addProcessAdapter(systemModel, linkModel);
        createdAdapters.add(linkAdapter);
        linkModel.setFigure(linkConnection);
        linkModel.addAttributesChangeListener(linkConnection);
        systemModel.getMainFolder(SystemModel.FolderKey.LINKS).add(linkModel);
        restoredFigures.add(linkConnection);
      }

      // Koordinaten der Location �ndern sich, wenn der Ma�stab ver�ndert wird
      origin.addListener(llf);
      llf.set(FigureConstants.ORIGIN, origin);
    }
  }

  private void restoreModelLocationTypes(List<LocationTypeModel> locTypeModels) {
    for (LocationTypeModel locTypeModel : locTypeModels) {
      procAdapterUtil.createProcessAdapter(locTypeModel,
                                           systemModel
                                           .getEventDispatcher());
    }
  }

  private void restoreModelLocationTypes(Set<LocationType> allLocationTypes,
                                         SystemModel systemModel,
                                         Set<ProcessAdapter> createdAdapters,
                                         Kernel kernel) {
    // --- Alle Location-Types, die der Kernel kennt ---
    for (LocationType locationType : allLocationTypes) {
      LocationTypeModel locationTypeModel = crsObjFactory.createLocationTypeModel();
      LocationTypeAdapter adapter = procAdapterFactory.createLocTypeAdapter(
          locationTypeModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      adapter.updateModelProperties(kernel,
                                    locationType,
                                    null);
      systemModel
          .getMainFolder(SystemModel.FolderKey.LOCATION_TYPES)
          .add(locationTypeModel);
    }
  }

  private void restoreModelLinks(List<LinkModel> linkModels) {
    for (LinkModel linkModel : linkModels) {
      procAdapterUtil.createProcessAdapter(linkModel,
                                           systemModel
                                           .getEventDispatcher());
    }
  }

  private void restoreModelVehicles(List<VehicleModel> vehicles) {
    for (VehicleModel vehModel : vehicles) {
      procAdapterUtil.createProcessAdapter(vehModel,
                                           systemModel
                                           .getEventDispatcher());
    }
  }

  private void restoreModelVehicles(Set<Vehicle> allVehicles,
                                    SystemModel systemModel,
                                    Set<ProcessAdapter> createdAdapters,
                                    Kernel kernel) {
    // --- Alle Fahrzeuge, die der Kernel kennt ---
    for (Vehicle vehicle : allVehicles) {
      VehicleModel vehicleModel = crsObjFactory.createVehicleModel();
      vehicleModel.setVehicle(vehicle);
      // Adapter zur Verkn�pfung des Kernel-Objekts mit der Figur
      VehicleAdapter adapter = procAdapterFactory.createVehicleAdapter(
          vehicleModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      // Setze Typ, Koordinaten, ... aus dem Kernel-Modell
      adapter.updateModelProperties(kernel,
                                    vehicle,
                                    null);
      systemModel.getMainFolder(SystemModel.FolderKey.VEHICLES).add(vehicleModel);
      // Die VehicleFigures werden erst in OpenTCSDrawingView.setVehicles() erzeugt
    }
  }

  private void restoreModelPaths(List<PathModel> paths,
                                 List<Figure> restoredFigures,
                                 Origin origin) {
    for (PathModel pathModel : paths) {
      PathConnection pathFigure = crsObjFactory.createPathConnection();
      pathFigure.set(FigureConstants.MODEL, pathModel);
      StringProperty stringProperty
          = (StringProperty) pathModel.getProperty(AbstractConnection.START_COMPONENT);
      PointModel startPointModel = getPointComponent(stringProperty.getText());
      stringProperty = (StringProperty) pathModel.getProperty(AbstractConnection.END_COMPONENT);
      PointModel endPointModel = getPointComponent(stringProperty.getText());
      if (startPointModel != null && endPointModel != null) {
        pathFigure.connect(startPointModel.getFigure(), endPointModel.getFigure());
      }

      SelectionProperty selectionProperty
          = (SelectionProperty) pathModel.getProperty(ElementPropKeys.PATH_CONN_TYPE);
      PathModel.LinerType connectionType = (PathModel.LinerType) selectionProperty.getValue();

      if (connectionType != null) {
        pathFigure.setLinerByType(connectionType);

        stringProperty
            = (StringProperty) pathModel.getProperty(ElementPropKeys.PATH_CONTROL_POINTS);
        String sControlPoints = stringProperty.getText();
        initPathControlPoints(connectionType, sControlPoints, pathFigure);
      }

      procAdapterUtil.createProcessAdapter(pathModel,
                                           systemModel
                                           .getEventDispatcher());
      pathModel.setFigure(pathFigure);
      pathModel.addAttributesChangeListener(pathFigure);
      restoredFigures.add(pathFigure);
      origin.addListener(pathFigure);
      pathFigure.set(FigureConstants.ORIGIN, origin);
    }
  }

  private void restoreModelPaths(Set<Path> allPaths, SystemModel systemModel,
                                 Origin origin,
                                 Set<ProcessAdapter> createdAdapters,
                                 List<Figure> restoredFigures,
                                 Kernel kernel) {
    // --- Alle Pfade, die der Kernel kennt ---
    for (Path path : allPaths) {
      // Neues Figure-Objekt
      PathConnection pathFigure = crsObjFactory.createPathConnection();
      // Das zugeh�rige Modell
      PathModel pathModel = pathFigure.getModel();
      // Anfangs- und Endpunkte
      PointModel startPointModel = (PointModel) getModelComponent(systemModel, path.getSourcePoint());
      PointModel endPointModel = (PointModel) getModelComponent(systemModel, path.getDestinationPoint());
      pathFigure.connect(startPointModel.getFigure(), endPointModel.getFigure());
      // Adapter zur Verkn�pfung des Kernel-Objekts mit der Figur
      PathAdapter adapter = procAdapterFactory.createPathAdapter(
          pathModel, systemModel.getEventDispatcher());
      adapter.register();

      createdAdapters.add(adapter);
      // Das zugeh�rige Model Layout Element suchen und mit dem Adapter verkn�pfen
      ModelLayoutElement layoutElement = systemModel.getLayoutMap().get(path.getReference());

      // Setze Typ, Koordinaten, ... aus dem Kernel-Modell
      adapter.updateModelProperties(kernel,
                                    path,
                                    layoutElement);

      pathFigure.updateDecorations();

      if (layoutElement != null) {
        Map<String, String> layoutProperties = layoutElement.getProperties();
        SelectionProperty property = (SelectionProperty) pathModel.getProperty(ElementPropKeys.PATH_CONN_TYPE);
        String sConnectionType = layoutProperties.get(ElementPropKeys.PATH_CONN_TYPE);

        if (sConnectionType != null && !sConnectionType.isEmpty()) {
          PathModel.LinerType connectionType
              = PathModel.LinerType.valueOfNormalized(sConnectionType);
          property.setValue(connectionType);
          pathFigure.setLinerByType(connectionType);

          String sControlPoints = layoutProperties.get(ElementPropKeys.PATH_CONTROL_POINTS);
          initPathControlPoints(connectionType, sControlPoints, pathFigure);
        }
      }

      pathModel.setFigure(pathFigure);
      pathModel.addAttributesChangeListener(pathFigure);
      systemModel.getMainFolder(SystemModel.FolderKey.PATHS).add(pathModel);
      restoredFigures.add(pathFigure);
      // Koordinaten der Kontrollpunkte �ndern sich, wenn der Ma�stab ver�ndert wird
      origin.addListener(pathFigure);
      pathFigure.set(FigureConstants.ORIGIN, origin);
    }
  }

  private void initPathControlPoints(PathModel.LinerType connectionType,
                                     String sControlPoints,
                                     PathConnection pathFigure) {
    if (connectionType.equals(PathModel.LinerType.BEZIER)) {
      // Format: x1,y1 or x1,y1;x2,y2
      if (sControlPoints != null && !sControlPoints.isEmpty()) {
        String[] values = sControlPoints.split("[,;]");

        try {
          if (values.length >= 2) {
            int xcp1 = Integer.parseInt(values[0]);
            int ycp1 = Integer.parseInt(values[1]);
            Point2D.Double cp1 = new Point2D.Double(xcp1, ycp1);

            if (values.length >= 4) {
              int xcp2 = Integer.parseInt(values[2]);
              int ycp2 = Integer.parseInt(values[3]);
              Point2D.Double cp2 = new Point2D.Double(xcp2, ycp2);
              pathFigure.addControlPoints(cp1, cp2);	// Cubic curve
            }
            else {
              pathFigure.addControlPoints(cp1, cp1);	// Quadratic curve
            }
          }
        }
        catch (NumberFormatException nfex) {
        }
      }
    }
  }

  private void restoreModelPoints(List<PointModel> points,
                                  List<Figure> restoredFigures,
                                  Origin origin, double scaleX, double scaleY) {
    for (PointModel pointModel : points) {
      LabeledPointFigure lpf = crsObjFactory.createPointFigure();
      PointFigure pointFigure = lpf.getPresentationFigure();
      pointFigure.setModel(pointModel);

      // The corresponding label
      TCSLabelFigure label = new TCSLabelFigure(pointModel.getName());

      Point2D.Double labelPosition;
      Point2D.Double figurePosition;
      double figurePositionX = 0;
      double figurePositionY = 0;
      StringProperty stringProperty = (StringProperty) pointModel.getProperty(ElementPropKeys.POINT_POS_X);
      String pointPosX = stringProperty.getText();
      stringProperty = (StringProperty) pointModel.getProperty(ElementPropKeys.POINT_POS_Y);
      String pointPosY = stringProperty.getText();
      if (pointPosX != null && pointPosY != null) {
        try {
          figurePositionX = Integer.parseInt(pointPosX);
          figurePositionY = Integer.parseInt(pointPosY);
        }
        catch (NumberFormatException ex) {
        }
      }

      // Label
      stringProperty = (StringProperty) pointModel.getProperty(ElementPropKeys.POINT_LABEL_OFFSET_X);
      String labelOffsetX = stringProperty.getText();
      stringProperty = (StringProperty) pointModel.getProperty(ElementPropKeys.POINT_LABEL_OFFSET_Y);
      String labelOffsetY = stringProperty.getText();
      // TODO: labelOrientationAngle auswerten
//			String labelOrientationAngle = layoutProperties.get(ElementPropKeys.POINT_LABEL_ORIENTATION_ANGLE);

      double labelPositionX;
      double labelPositionY;
      if (labelOffsetX != null && labelOffsetY != null) {
        try {
          labelPositionX = Integer.parseInt(labelOffsetX);
          labelPositionY = Integer.parseInt(labelOffsetY);
        }
        catch (NumberFormatException ex) {
          // XXX This does not look right.
          labelPositionX = labelPositionY = -20;
        }

        labelPosition = new Point2D.Double(labelPositionX, labelPositionY);
        label.setOffset(labelPosition);
      }
      // Figur auf diese Position verschieben
      figurePosition = new Point2D.Double(figurePositionX / scaleX, -figurePositionY / scaleY);	// Vorzeichen!
      pointFigure.setBounds(figurePosition, figurePosition);

      labelPosition = pointFigure.getStartPoint();
      labelPosition.x += label.getOffset().x;
      labelPosition.y += label.getOffset().y;
      label.setBounds(labelPosition, null);
      lpf.setLabel(label);

      pointModel.setFigure(lpf);
      pointModel.addAttributesChangeListener(lpf);
      restoredFigures.add(lpf);

      procAdapterUtil.createProcessAdapter(pointModel,
                                           systemModel
                                           .getEventDispatcher());
      // Koordinaten der Punkte �ndern sich, wenn der Ma�stab ver�ndert wird
      origin.addListener(lpf);
      lpf.set(FigureConstants.ORIGIN, origin);
    }
  }

  private void restoreModelPoints(Set<Point> allPoints, SystemModel systemModel,
                                  Origin origin, double scaleX, double scaleY,
                                  Set<ProcessAdapter> adapters,
                                  List<Figure> restoredFigures,
                                  Kernel kernel) {
    // --- Alle Punkte, die der Kernel kennt ---
    for (Point point : allPoints) {
      // Neues Figure-Objekt
      LabeledPointFigure lpf = crsObjFactory.createPointFigure();
      PointFigure pointFigure = lpf.getPresentationFigure();
      // Das zugeh�rige Modell
      PointModel pointModel = pointFigure.getModel();
      // Adapter zur Verkn�pfung des Kernel-Objekts mit der Figur
      PointAdapter adapter = procAdapterFactory.createPointAdapter(
          pointModel, systemModel.getEventDispatcher());
      adapter.register();

      // Das zugeh�rige Model Layout Element suchen und mit dem Adapter verkn�pfen
      ModelLayoutElement layoutElement = systemModel.getLayoutMap().get(point.getReference());
      // Setze Typ, Koordinaten, ... aus dem Kernel-Modell
      adapter.updateModelProperties(kernel,
                                    point,
                                    layoutElement);
      adapters.add(adapter);
      // Die im Kernel gespeicherte Position
      double figurePositionX = point.getPosition().getX();
      double figurePositionY = point.getPosition().getY();
      // TODO: positionZ = point.getPosition().getZ();	// immer 0
      // Die zugeh�rige Beschriftung:
      TCSLabelFigure label = new TCSLabelFigure(point.getName());

      Point2D.Double labelPosition;
      Point2D.Double figurePosition;
      if (layoutElement != null) {
        Map<String, String> layoutProperties = layoutElement.getProperties();
        String pointPosX = layoutProperties.get(ElementPropKeys.POINT_POS_X);
        String pointPosY = layoutProperties.get(ElementPropKeys.POINT_POS_Y);
        // Die in den Properties gespeicherte Position �berschreibt die im Kernel-Objekt gespeicherten Werte
        // TO DO: Auswahl, z.B. �ber Parameter?
        if (pointPosX != null && pointPosY != null) {
          try {
            figurePositionX = Integer.parseInt(pointPosX);
            figurePositionY = Integer.parseInt(pointPosY);
          }
          catch (NumberFormatException ex) {
          }
        }

        // Label
        String labelOffsetX = layoutProperties.get(ElementPropKeys.POINT_LABEL_OFFSET_X);
        String labelOffsetY = layoutProperties.get(ElementPropKeys.POINT_LABEL_OFFSET_Y);
        // TODO: labelOrientationAngle auswerten
//			String labelOrientationAngle = layoutProperties.get(ElementPropKeys.POINT_LABEL_ORIENTATION_ANGLE);

        double labelPositionX;
        double labelPositionY;
        if (labelOffsetX != null && labelOffsetY != null) {
          try {
            labelPositionX = Integer.parseInt(labelOffsetX);
            labelPositionY = Integer.parseInt(labelOffsetY);
          }
          catch (NumberFormatException ex) {
            // XXX This does not look right.
            labelPositionX = labelPositionY = -20;
          }

          labelPosition = new Point2D.Double(labelPositionX, labelPositionY);
          label.setOffset(labelPosition);
        }
      }
      // Figur auf diese Position verschieben
      figurePosition = new Point2D.Double(figurePositionX / scaleX, -figurePositionY / scaleY);	// Vorzeichen!
      pointFigure.setBounds(figurePosition, figurePosition);

      labelPosition = pointFigure.getStartPoint();
      labelPosition.x += label.getOffset().x;
      labelPosition.y += label.getOffset().y;
      label.setBounds(labelPosition, null);
      lpf.setLabel(label);

      pointModel.setFigure(lpf);
      pointModel.addAttributesChangeListener(lpf);
      systemModel.getMainFolder(SystemModel.FolderKey.POINTS).add(pointModel);
      restoredFigures.add(lpf);

      // Koordinaten der Punkte �ndern sich, wenn der Ma�stab ver�ndert wird
      origin.addListener(lpf);
      lpf.set(FigureConstants.ORIGIN, origin);
    }
  }

  private LayoutAdapter createLayoutAdapter(SystemModel systemModel,
                                            LayoutModel model) {
    LayoutAdapter adapter = procAdapterFactory.createLayoutAdapter(
        model, systemModel.getEventDispatcher());
    adapter.register();

    try {
      systemModel.getEventDispatcher().addProcessAdapter(adapter);
    }
    catch (KernelRuntimeException ex) {
      log.log(Level.SEVERE, "Exception in creating process object", ex);
    }

    return adapter;
  }

  /**
   * Shows a dialog to select a model to load.
   *
   * @return The selected file or <code>null</code> if nothing was selected.
   */
  private File showOpenDialog() {
    File dataDir
        = new File(System.getProperty("user.dir") + File.separator + MODEL_DIRECTORY);
    if (!dataDir.isDirectory()) {
      dataDir.mkdir();
    }
    JFileChooser fileChooser
        = new JFileChooser(dataDir);
    int returnVal = fileChooser.showOpenDialog(null);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      return fileChooser.getSelectedFile();
    }
    else {
      return null;
    }
  }

  /**
   * Shows a dialog to save a model locally.
   *
   * @return true, if the user chose a file, false otherwise.
   */
  private boolean showSaveDialog() {
    File dataDir
        = new File(System.getProperty("user.dir") + File.separator + MODEL_DIRECTORY);
    if (!dataDir.isDirectory()) {
      dataDir.mkdir();
    }
    JFileChooser fileChooser
        = new JFileChooser(dataDir);
    int returnVal = fileChooser.showSaveDialog(null);

    File selectedFile;
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      selectedFile = fileChooser.getSelectedFile();
    }
    else {
      fModelName = Kernel.DEFAULT_MODEL_NAME;
      return false;
    }

    fModelName = selectedFile.getName().replaceFirst("[.][^.]+$", ""); //remove extension;
    if (fModelName.isEmpty()) {
      fModelName = Kernel.DEFAULT_MODEL_NAME;
      return false;
    }

    return true;
  }

  /**
   * Erzeugt zu einer gelesenen Verkn�pfung einen passenden ProcessAdapter und
   * f�gt ihn dem EventDispatcher hinzu.
   *
   * @param systemModel
   * @param link Die Verkn�pfung
   * @param point Der Meldepunkt
   * @param location Die Station
   * @return Der erzeugte ProcessAdapter
   */
  private LinkAdapter addProcessAdapter(SystemModel systemModel,
                                        LinkModel link) {
    LinkAdapter linkAdapter = procAdapterFactory.createLinkAdapter(
        link, systemModel.getEventDispatcher());
    linkAdapter.register();

    return linkAdapter;
  }

  /**
   * Findet zu einem TCSObject das passende Objekt in der Modellierung.
   *
   * @param systemModel
   * @param ref die Referenz auf das TCSObject
   * @return das Objekt in der Modellierung
   */
  private ModelComponent getModelComponent(SystemModel systemModel,
                                           TCSObjectReference<?> ref) {
    ProcessAdapter adapter = systemModel.getEventDispatcher().findProcessAdapter(ref);

    if (adapter == null) {
      return null;
    }

    return adapter.getModel();
  }

  /**
   * Return the point model component with the given name from the
   * system model.
   *
   * @param name The name of the point to return.
   * @return The PointModel that matches the given name.
   */
  private PointModel getPointComponent(String name) {
    for (PointModel modelComponent : systemModel.getPointModels()) {
      if (modelComponent.getName().equals(name)) {
        return modelComponent;
      }
    }
    return null;
  }

  /**
   * Returns the location type model component with the given name from the
   * system model.
   *
   * @param name The name of the location type to return.
   * @return The LocationModel that matches the given name.
   */
  private LocationTypeModel getLocationTypeComponent(String name) {
    for (LocationTypeModel modelComponent : systemModel.getLocationTypeModels()) {
      if (modelComponent.getName().equals(name)) {
        return modelComponent;
      }
    }
    return null;
  }

  /**
   * Returns a <code>ModelComponent</code> with the given name that is
   * a member of a block.
   *
   * @param name The name of the ModelComponent to return.
   * @return The ModelComponent.
   */
  private ModelComponent getBlockMember(String name) {
    for (PointModel pModel : systemModel.getPointModels()) {
      if (name.equals(pModel.getName())) {
        return pModel;
      }
    }
    for (PathModel pModel : systemModel.getPathModels()) {
      if (name.equals(pModel.getName())) {
        return pModel;
      }
    }
    for (LocationModel lModel : systemModel.getLocationModels()) {
      if (name.equals(lModel.getName())) {
        return lModel;
      }
    }
    return null;
  }

  /**
   * Returns a <code>ModelComponent</code> with the given name that is
   * a member of a group.
   *
   * @param name The name of the ModelComponent to return.
   * @return The ModelComponent.
   */
  private ModelComponent getGroupMember(String name) {
    return getBlockMember(name);
  }

  /**
   * Returns the attached links to the given location model. After persisting
   * the LinkModels in the system model contain the names of the
   * connected components in the specific properties. The components are
   * searched here and are set as the connected components in the link.
   *
   * @param locationModel The LocationModel for which we need the connected
   * links.
   * @return A list with the connected links.
   */
  private List<LinkModel> getAttachedLinks(LocationModel locationModel) {
    List<LinkModel> links = new ArrayList<>();
    String locationName = locationModel.getName();
    for (LinkModel link : systemModel.getLinkModels()) {
      StringProperty startProperty
          = (StringProperty) link.getProperty(AbstractConnection.START_COMPONENT);
      StringProperty endProperty
          = (StringProperty) link.getProperty(AbstractConnection.END_COMPONENT);
      if (startProperty.getText().equals(locationName)) {
        PointModel endComponent = getPointComponent(endProperty.getText());
        link.setConnectedComponents(locationModel, endComponent);
        links.add(link);
      }
      else if (endProperty.getText().equals(locationName)) {
        PointModel startComponent = getPointComponent(startProperty.getText());
        link.setConnectedComponents(startComponent, locationModel);
        links.add(link);
      }
    }

    return links;
  }
}
