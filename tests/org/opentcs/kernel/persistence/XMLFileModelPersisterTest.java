/*
 * openTCS copyright information:
 * Copyright (c) 2014 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.kernel.persistence;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Provider;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opentcs.TestEnvironment;
import org.opentcs.kernel.workingset.Model;
import org.opentcs.kernel.workingset.TCSObjectPool;

/**
 *
 * @author Tobias Marquardt (Fraunhofer IML)
 */
public class XMLFileModelPersisterTest {

  /**
   * The name of the test model.
   */
  private static final String MODEL_NAME = "Testmodel";
  private static final String OTHER_MODEL_NAME = "Testmodel2";
  /**
   * The persister instance for testing.
   */
  private ModelPersister persister;
  /**
   * The XMLModelReader used by the persister.
   */
  private XMLModelReader reader;
  /**
   * The XMLModelWriter used by the persister.
   */
  private XMLModelWriter writer;
  /**
   * The model saved by XMLModelWriter.
   */
  private Model persistedModel;

  /**
   * {@inheritDoc}
   *
   * @throws java.io.IOException
   * @throws org.opentcs.kernel.persistence.InvalidModelException
   */
  @Before
  public void setUp()
      throws IOException, InvalidModelException {
    reader = mock(XMLModelReader.class);
    writer = mock(XMLModelWriter.class);
    // Store the model passed to writeXMLModel()
    doAnswer(new Answer<Object>() {
      @Override
      @SuppressWarnings("unchecked")
      public Object answer(InvocationOnMock invocation)
          throws Throwable {
        Object[] args = invocation.getArguments();
        persistedModel = (Model) args[0];
        persistedModel.setName(
            ((Optional<String>) args[1]).orElse(persistedModel.getName()));
        return null;
      }
    }).when(writer).writeXMLModel(
        any(Model.class), any(), any(OutputStream.class));
    // We don't really load the model on readXMLModel file, but make sure
    // it has at least the same name as the persisted model.
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation)
          throws Throwable {
        Model model = (Model) invocation.getArguments()[1];
        model.setName(persistedModel.getName());
        return null;
      }
    }).when(reader).readXMLModel(any(InputStream.class), any(Model.class));
    // Create persister with providers that return the mocked objects
    persister = new XMLFileModelPersister(
        TestEnvironment.getKernelHomeDirectory(),
        new XMLModelReaderProvider(reader),
        new XMLModelWriterProvider(writer));
  }

  /**
   * {@inheritDoc}
   */
  @After
  public void tearDown() {
    persister = null;
    reader = null;
    writer = null;
    persistedModel = null;
    deleteBackups();
  }

  @Test
  public void testSaveModelShouldWriteXMLModel()
      throws IOException {
    Model model = createTestModel(MODEL_NAME);
    persister.saveModel(model, Optional.ofNullable(null));
    //TODO How to use Hamcrest's Matchers.sameInstance(model) here?
    //     We would not need the assertEquals and the persistedModel member anymore...
    verify(writer, times(1))
        .writeXMLModel(any(Model.class), any(), any(OutputStream.class));
    assertEquals(persistedModel, model);
  }

  @Test
  public void testLoadModelShouldReadXMLModel()
      throws IOException, InvalidModelException {
    persister.saveModel(createTestModel(MODEL_NAME), Optional.ofNullable(null));
    Model model = new Model(new TCSObjectPool());
    persister.loadModel(model);
    verify(reader, atLeastOnce())
        .readXMLModel(any(InputStream.class), any(Model.class));
  }

  @Test
  public void testModelNameShouldNotBePresentAfterRemovingModel()
      throws IOException {
    persister.saveModel(createTestModel(MODEL_NAME), Optional.of(MODEL_NAME));
    deleteBackups();
    persister.removeModel();
    assertFalse(persister.getModelName().isPresent());
  }

  @Test
  public void testSavedModelNameShouldBeAsSpecified()
      throws IOException {
    persister.saveModel(createTestModel(MODEL_NAME),
                        Optional.ofNullable(OTHER_MODEL_NAME));
    assertEquals(OTHER_MODEL_NAME, persister.getModelName().orElse(null));
    deleteBackups();
    persister.saveModel(createTestModel(MODEL_NAME), Optional.empty());
    assertEquals(MODEL_NAME, persister.getModelName().orElse(null));
  }

  @Test
  public void testLoadModelShouldReturnEmptyModelIfNoModelIsSaved()
      throws IOException {
    persister.removeModel();
    Model model = new Model(new TCSObjectPool());
    persister.loadModel(model);
    assertTrue(model.getPoints(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getBlocks(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getGroups(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getLocations(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getLocationTypes(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getPaths(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getStaticRoutes(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getVehicles(Pattern.compile(".*")).isEmpty());
    assertTrue(model.getName().isEmpty());
  }

  @Test
  public void testShouldHaveModelAfterSavingModel()
      throws IOException {
    persister.saveModel(createTestModel(MODEL_NAME), Optional.of(MODEL_NAME));
    assertTrue(persister.hasSavedModel());
  }

  @Test
  public void testShouldNotHaveModelAfterRemovingModel()
      throws IOException {
    persister.saveModel(createTestModel(MODEL_NAME), Optional.of(MODEL_NAME));
    deleteBackups();
    persister.removeModel();
    assertFalse(persister.hasSavedModel());
  }

  private Model createTestModel(String name) {
    Model model = new Model(new TCSObjectPool());
    model.createPoint(null);
    model.createVehicle(null);
    model.setName(name);
    return model;
  }

  /**
   * Delete all backup files from the backups-directory if it does exist.
   */
  private void deleteBackups() {
    File backupDir
        = new File(TestEnvironment.getKernelHomeDirectory(), "data/backups");
    if (!backupDir.isDirectory()) {
      return;
    }
    for (File file : backupDir.listFiles()) {
      if (file.getName().contains("_backup_")) {
        file.delete();
      }
    }
  }

  /**
   * A simple provider for XMLModelWriter that always returns the instance it
   * was initialized with.
   */
  private static class XMLModelWriterProvider
      implements Provider<XMLModelWriter> {

    private final XMLModelWriter writer;

    private XMLModelWriterProvider(XMLModelWriter writer) {
      this.writer = Objects.requireNonNull(writer);
    }

    @Override
    public XMLModelWriter get() {
      return writer;
    }
  }

  /**
   * A simple provider for XMLModelReader that always returns the instance it
   * was initialized with.
   */
  private static class XMLModelReaderProvider
      implements Provider<XMLModelReader> {

    private final XMLModelReader reader;

    private XMLModelReaderProvider(XMLModelReader reader) {
      this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public XMLModelReader get() {
      return reader;
    }
  }
}
