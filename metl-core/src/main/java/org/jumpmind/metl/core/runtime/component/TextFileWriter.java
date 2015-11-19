/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.core.runtime.component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.runtime.ControlMessage;
import org.jumpmind.metl.core.runtime.LogLevel;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;
import org.jumpmind.metl.core.runtime.resource.IStreamable;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.util.FormatUtils;

public class TextFileWriter extends AbstractComponentRuntime {

    public static final String TYPE = "Text File Writer";

    public final static String DEFAULT_ENCODING = "UTF-8";

    public final static String TEXTFILEWRITER_ENCODING = "textfilewriter.encoding";

    public final static String TEXTFILEWRITER_RELATIVE_PATH = "textfilewriter.relative.path";

    public static final String TEXTFILEWRITER_MUST_EXIST = "textfilewriter.must.exist";

    public static final String TEXTFILEWRITER_APPEND = "textfilewriter.append";

    public static final String TEXTFILEWRITER_TEXT_LINE_TERMINATOR = "textfilewriter.text.line.terminator";

    public static final String TEXTFILEWRITER_GET_FILE_FROM_MESSAGE = "textfilewriter.get.file.name.from.message";

    public static final String TEXTFILEWRITER_FILENAME_PROPERTY = "textfilewriter.filename.property";

    String encoding;

    String relativePathAndFile;

    boolean mustExist;

    boolean append;

    String lineTerminator;

    boolean getFileNameFromMessage;
    
    String fileNameFromMessageProperty;
    
    BufferedWriter bufferedWriter = null;

    @Override
    protected void start() {
        TypedProperties properties = getTypedProperties();
        Component component = getComponent();
        relativePathAndFile = FormatUtils.replaceTokens(properties.get(TEXTFILEWRITER_RELATIVE_PATH), context.getFlowParametersAsString(),
                true);
        mustExist = properties.is(TEXTFILEWRITER_MUST_EXIST);
        append = properties.is(TEXTFILEWRITER_APPEND);
        lineTerminator = properties.get(TEXTFILEWRITER_TEXT_LINE_TERMINATOR);
        encoding = properties.get(TEXTFILEWRITER_ENCODING, DEFAULT_ENCODING);
        getFileNameFromMessage = component.getBoolean(TEXTFILEWRITER_GET_FILE_FROM_MESSAGE, getFileNameFromMessage);
        fileNameFromMessageProperty = component.get(TEXTFILEWRITER_FILENAME_PROPERTY, fileNameFromMessageProperty);
        
        if (lineTerminator != null) {
            lineTerminator = StringEscapeUtils.unescapeJava(properties.get(TEXTFILEWRITER_TEXT_LINE_TERMINATOR));
        }
    }

    @Override
    public boolean supportsStartupMessages() {
        return false;
    }

    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        if (getResourceRuntime() == null) {
            throw new IllegalStateException("The msgTarget resource has not been configured.  Please choose a resource.");
        }

        initStreamAndWriter(inputMessage);
        
        try {
            Object payload = inputMessage.getPayload();
            if (payload instanceof ArrayList) {
                ArrayList<?> recs = (ArrayList<?>) payload;
                for (Object rec : recs) {
                    bufferedWriter.write(rec != null ? rec.toString() : "");
                    if (lineTerminator != null) {
                        bufferedWriter.write(lineTerminator);
                    } else {
                        bufferedWriter.newLine();
                    }
                }
                bufferedWriter.flush();

            } else if (payload instanceof String) {
                bufferedWriter.write((String) payload);
            } else {
                bufferedWriter.write("");
            }
        } catch (IOException e) {
            throw new IoException(e);
        }
        
        if (unitOfWorkBoundaryReached && callback != null) {
            close();
            callback.sendMessage(null, "{\"status\":\"success\"}");
        }
    }

    private void initStreamAndWriter(Message inputMessage) {
    	String messageHeaderFileName = null;
    	
    	if (getFileNameFromMessage && !(inputMessage instanceof ControlMessage)) {
    		Object fileName = inputMessage.getHeader().get(fileNameFromMessageProperty);
			if (fileName == null || ((String) fileName).length() == 0) {
				throw new RuntimeException("Configuration determines that the file name should be in "
						+ "the message header but was not.  Verify the property " + 
						fileNameFromMessageProperty + " is being passed into the message header");
    		}
    		messageHeaderFileName = (String) fileName;
    	}
    
        if (bufferedWriter == null) {
            IStreamable streamable = (IStreamable) getResourceReference();
            if (!append && streamable.supportsDelete() && !(inputMessage instanceof ControlMessage)) {
                if (getFileNameFromMessage) {
                	streamable.delete(messageHeaderFileName);
                }
                else {
                	streamable.delete(relativePathAndFile);
                }
            }
            log(LogLevel.INFO, String.format("Writing text file to %s", streamable.toString()));
            if (getFileNameFromMessage && !(inputMessage instanceof ControlMessage)) {
            	bufferedWriter = initializeWriter(streamable.getOutputStream(messageHeaderFileName, mustExist));
            }
            else {
            	bufferedWriter = initializeWriter(streamable.getOutputStream(relativePathAndFile, mustExist));
            }
        }
    }

    private BufferedWriter initializeWriter(OutputStream stream) {
        try {
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(stream, encoding));
        } catch (UnsupportedEncodingException e) {
            throw new IoException("Error creating buffered writer " + e.getMessage());
        }
        return bufferedWriter;
    }

    private void close() {
        IOUtils.closeQuietly(bufferedWriter);
        bufferedWriter = null;
    }

}
