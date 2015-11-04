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
package org.jumpmind.metl.ui.mapping;

import java.util.Collections;
import java.util.List;

import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.model.ComponentAttributeSetting;
import org.jumpmind.metl.core.runtime.component.Mapping;
import org.jumpmind.metl.ui.common.ApplicationContext;
import org.jumpmind.metl.ui.common.ModelEntitySorter;

import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.ui.JavaScriptFunction;

import elemental.json.JsonArray;
import elemental.json.JsonObject;

@JavaScript({ "dom.jsPlumb-1.7.5-min.js", "mapping-diagram.js" })
@StyleSheet({ "mapping-diagram.css" })
@SuppressWarnings("serial")
public class MappingDiagram extends AbstractJavaScriptComponent {

	ApplicationContext context;

	Component component;
	
    String selectedSourceId;
    
    String selectedTargetId;

	public MappingDiagram(ApplicationContext context, Component component) {
		this.context = context;
		this.component = component;
        setPrimaryStyleName("mapping-diagram");
        setId("mapping-diagram");

        MappingDiagramState state = getState();
        state.component = component;
        
        state.inputModel = component.getInputModel();
        if (state.inputModel != null) {
            context.getConfigurationService().refresh(state.inputModel);
            Collections.sort(state.inputModel.getModelEntities(), new ModelEntitySorter());
            state.inputModel.sortAttributes();
        }
        
        state.outputModel = component.getOutputModel();
        if (state.outputModel != null) {          
            context.getConfigurationService().refresh(state.outputModel);
            Collections.sort(state.outputModel.getModelEntities(), new ModelEntitySorter());
            state.outputModel.sortAttributes();
        }

        addFunction("onSelect", new OnSelectFunction());       
        addFunction("onConnection", new OnConnectionFunction());
    }
    
    @Override
    protected MappingDiagramState getState() {
        return (MappingDiagramState) super.getState();
    }

    public void removeSelected() {
    	if (selectedSourceId != null) {
    		removeConnection(selectedSourceId, selectedTargetId);
    	}
    }

    public void filterInputModel(String text) {
        callFunction("filterInputModel", text);
    }

    public void filterOutputModel(String text) {
        callFunction("filterOutputModel", text);
    }

    protected void removeConnection(String sourceId, String targetId) {
    	List<ComponentAttributeSetting> settings = component.getAttributeSetting(sourceId, Mapping.ATTRIBUTE_MAPS_TO);
    	for (ComponentAttributeSetting setting : settings) {
    		if (setting.getValue().equals(targetId)) {
        		component.getAttributeSettings().remove(setting);
        		context.getConfigurationService().delete(setting);
        		markAsDirty();
    		}
    	}
    	if (sourceId.equals(selectedSourceId) && targetId.equals(selectedTargetId)) {
    	    selectedSourceId = selectedTargetId = null;
    	    fireEvent(new SelectEvent(MappingDiagram.this, selectedSourceId, selectedTargetId));
    	}
    }
    
    class OnSelectFunction implements JavaScriptFunction {
    	public void call(JsonArray arguments) {
            if (arguments.length() > 0) {
                JsonObject json = arguments.getObject(0);
                selectedSourceId = json.getString("sourceId").substring(3);
                selectedTargetId = json.getString("targetId").substring(3);
            } else {
            	selectedSourceId = selectedTargetId = null;
            }
            fireEvent(new SelectEvent(MappingDiagram.this, selectedSourceId, selectedTargetId));
    	}
    }

    class OnConnectionFunction implements JavaScriptFunction {
		public void call(JsonArray arguments) {
            if (arguments.length() > 0) {
                JsonObject json = arguments.getObject(0);
                String sourceId = json.getString("sourceId").substring(3);
                String targetId = json.getString("targetId").substring(3);
                boolean removed = json.getBoolean("removed");
                if (removed) {
                	removeConnection(sourceId, targetId);
                } else {
                	ComponentAttributeSetting setting = new ComponentAttributeSetting();
                	setting.setAttributeId(sourceId);
                	setting.setComponentId(component.getId());
            		setting.setName(Mapping.ATTRIBUTE_MAPS_TO);
            		component.addAttributeSetting(setting);
                	setting.setValue(targetId);
                	context.getConfigurationService().save(setting);
                	markAsDirty();
                }
            }
		}
    }
}
