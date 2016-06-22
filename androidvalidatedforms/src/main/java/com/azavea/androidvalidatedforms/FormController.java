package com.azavea.androidvalidatedforms;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.azavea.androidvalidatedforms.controllers.FormSectionController;
import com.azavea.androidvalidatedforms.controllers.LabeledFieldController;
import com.azavea.androidvalidatedforms.validations.PerFieldValidationErrorDisplay;
import com.azavea.androidvalidatedforms.validations.ValidationError;
import com.azavea.androidvalidatedforms.validations.ValidationErrorDisplay;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <code>FormController</code> is the main class that manages the form elements of NexusDialog. It provides simple APIs
 * to quickly create and manage form fields. Typically, an instance of this class is created within an Activity or Fragment.
 * <p/>
 * The form's data is backed by a model represented by {@link FormModel}, which provides a generic interface to access
 * the data. Form elements use the model to retrieve current field values and set them upon user input. By default,
 * <code>FormController</code> uses a default Map-based model keyed by the element's names. You can also use a custom
 * implementation of a <code>FormModel</code> if desired.
 */
public class FormController {
    private final List<FormSectionController> sectionControllers = new ArrayList<FormSectionController>();

    private final Object modelObj;
    private FormModel model;

    private final Context context;
    private ValidationErrorDisplay validationErrorDisplay;
    private static final AtomicInteger nextGeneratedViewId = new AtomicInteger(1);

    public FormController(Context context, Object modelObj) {
        this.context = context;
        this.modelObj = modelObj;
        setModel();
        setValidationErrorsDisplayMethod(new PerFieldValidationErrorDisplay(context, this));
    }

    /**
     * Returns the associated model of this form.
     *
     * @return the associated model of this form
     */
    public FormModel getModel() {
        return model;
    }

    /**
     * Returns the associated model object (containing the data) for this form.
     *
     * @return Model object for this form
     */
    protected Object getModelObject() {
        return modelObj;
    }

    /**
     * Instantiates form model based on the modelObj class
     *
     * @return FormModel that gets and sets publicly accessible fields on the model class
     */
    private FormModel createFormModel() {
        final Object modelObject = getModelObject();
        final Class modelObjectClass = modelObject.getClass();

        return new FormModel() {
            @Override
            protected void setBackingValue(String name, Object newValue) {
                try {
                    modelObjectClass.getField(name).set(modelObject, newValue);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public Class getBackingModelClass(String fieldName) {
                try {
                    return modelObjectClass.getField(fieldName).getType();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected Object getBackingValue(String name) {
                try {
                    return modelObjectClass.getField(name).get(modelObject);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public Object getBackingModelObject() {
                return modelObject;
            }
        };
    }

    /**
     * Sets the model to use for this form
     *
     */
    private void setModel() {
        this.model = createFormModel();
        registerFormModelListener();
    }

    private void registerFormModelListener() {
        // unregister listener first to make sure we only have one listener registered.
        getModel().removePropertyChangeListener(modelListener);
        getModel().addPropertyChangeListener(modelListener);
    }

    /**
     * Generate an available ID for the view.
     * Uses the same implementation as {@link View#generateViewId}
     *
     * @return the next available view identifier.
     */
    public static int generateViewId(){
        for (;;) {
            final int result = nextGeneratedViewId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (nextGeneratedViewId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    /**
     * Returns a list of the sections of this form.
     *
     * @return a list containing all the <code>FormSectionController</code>'s of this form
     */
    public List<FormSectionController> getSections() {
        return sectionControllers;
    }

    /**
     * Returns the corresponding <code>FormSectionController</code> from the specified name.
     *
     * @param name  the name of the section
     * @return      the instance of <code>FormSectionController</code> with the specified name, or null if no such
     *              section exists
     */
    public FormSectionController getSection(String name) {
        for (FormSectionController section : getSections()) {
            if (section.getName().equals(name)) {
                return section;
            }
        }
        return null;
    }

    /**
     * Adds the specified section to the form.
     *
     * @param section   the form section to add
     * @param position  the position at which to insert the section
     */
    public void addSection(FormSectionController section, int position) {
        sectionControllers.add(position, section);
    }

    /**
     * Adds the specified section to the form to the end.
     *
     * @param section   the form section to add
     */
    public void addSection(FormSectionController section) {
        addSection(section, sectionControllers.size());
    }

    /**
     * Returns the corresponding <code>FormElementController</code> from the specified name.
     *
     * @param name  the name of the form element
     * @return      the instance of <code>FormElementController</code> with the specified name, or null if no such
     *              element exists
     */
    public FormElementController getElement(String name) {
        for (FormSectionController section : getSections()) {
            FormElementController element = section.getElement(name);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    /**
     * Returns the total number of elements in this form, not including sections.
     *
     * @return  the total number of elements in this form, not including sections
     */
    public int getNumberOfElements() {
        int count = 0;
        for (FormSectionController section : getSections()) {
            count += section.getElements().size();
        }

        return count;
    }

    /**
     * Refreshes the view of all elements in this form to reflect current model values
     */
    public void refreshElements() {
        for (FormSectionController section : getSections()) {
            section.refresh();
        }
    }

    /**
     * Returns a list of validation errors of the form's input
     *
     * @return a list of validation errors of the form's input
     */
    public List<ValidationError> validateInput() {
        Log.d("FormController", "Running validateInput");

        List<ValidationError> errors = new ArrayList<ValidationError>();

        for (FormSectionController section : getSections()) {
            for (FormElementController element : section.getElements()) {
                if (element instanceof LabeledFieldController) {
                    LabeledFieldController field = (LabeledFieldController)element;
                    errors.addAll(field.validateInput());
                }
            }
        }

        return errors;
    }

    /**
     * Indicates if the current user input is valid.
     *
     * @return  true if the current user input is valid, otherwise false
     */
    public boolean isValidInput() {
        return validateInput().isEmpty();
    }

    /**
     * Shows an appropriate error message if there are validation errors in the form's input.
     */
    public void showValidationErrors() {
        validationErrorDisplay.showErrors(validateInput());
    }

    /**
     * Remove every validation errors from the form.
     * Is actually a proxy call to {@link ValidationErrorDisplay#resetErrors()}.
     */
    public void resetValidationErrors() {
        validationErrorDisplay.resetErrors();
    }

    /**
     * Change the display method for validation errors
     *
     * @param method the method to use.
     */
    public void setValidationErrorsDisplayMethod(ValidationErrorDisplay method) {
        this.validationErrorDisplay = method;
    }

    /**
     * Adds all the form elements that were added to this <code>FormController</code> inside the specified
     * <code>ViewGroup</code>. This method should be called once the form elements have been added to this controller.
     *
     * @param containerView the view container to add the form elements within
     */
    public void recreateViews(ViewGroup containerView) {
        containerView.removeAllViews();

        for (FormSectionController section : getSections()) {
            ((FormElementController)section).setModel(getModel());
            containerView.addView(section.getView());

            for (FormElementController element : section.getElements()) {
                element.setModel(getModel());
                containerView.addView(element.getView());
            }
        }

        // now that the view is setup, register a listener of the model to update the view on changes
        registerFormModelListener();
    }

    private PropertyChangeListener modelListener = new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent event) {
            getElement(event.getPropertyName()).refresh();
        }
    };
}
