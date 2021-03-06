/*
 * openTCS copyright information:
 * Copyright (c) 2014 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import static java.util.Objects.requireNonNull;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.opentcs.guing.model.ModelComponent;
import org.opentcs.guing.persistence.CourseElement;
import org.opentcs.guing.persistence.CourseModel;
import org.opentcs.guing.persistence.ModelComponentConverter;

/**
 * Synchronizes data kept in <code>ModelComponents</code> to a xml file.
 *
 * @author Tobias Marquardt (Fraunhofer IML)
 */
public class ModelJAXBPersistor
    implements ModelPersistor {

  /**
   * The file where the xml generated by JAXB is written to.
   */
  private final File file;
  /**
   * The course model that is converted to xml.
   */
  private final CourseModel courseModel;
  /**
   * Converts ModelComponents to JAXB classes.
   */
  private final ModelComponentConverter modelConverter;

  /**
   * Create a new instance.
   *
   * @param file The target file.
   */
  public ModelJAXBPersistor(File file) {
    this.file = requireNonNull(file, "file");
    this.courseModel = new CourseModel();
    modelConverter = new ModelComponentConverter();
  }

  @Override
  public void init() {
    // Do nada.
  }

  @Override
  public void persist(ModelComponent model) {
    requireNonNull(model, "model");
    CourseElement element = modelConverter.convertModel(model);
    courseModel.add(element);
  }

  @Override
  public void close()
      throws IOException {
    try (OutputStream outStream = new FileOutputStream(file)) {
      JAXBContext jc = JAXBContext.newInstance(CourseModel.class);
      Marshaller marshaller = jc.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      StringWriter stringWriter = new StringWriter();
      marshaller.marshal(courseModel, stringWriter);

      outStream.write(stringWriter.toString().getBytes(Charset.forName("UTF-8")));
      outStream.flush();
      outStream.close();
    }
    catch (JAXBException e) {
      throw new IOException(e);
    }
  }
}
