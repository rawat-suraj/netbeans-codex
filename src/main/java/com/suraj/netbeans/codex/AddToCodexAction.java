/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.suraj.netbeans.codex;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.loaders.DataObject;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(
        category = "Tools",
        id = "com.suraj.netbeans.codex.AddToCodexAction"
)
@ActionRegistration(displayName = "Add to Codex")
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 1500),
    @ActionReference(
            path = "Loaders/text/x-php5/Actions",
            position = 1500),
    @ActionReference(
            path = "Loaders/text/x-java/Actions",
            position = 1500),
    @ActionReference(
            path = "Loaders/text/plain/Actions",
            position = 1500)
})

/**
 *
 * @author suraj
 */
public class AddToCodexAction implements ActionListener {

    private final List<DataObject> context;

    public AddToCodexAction(List<DataObject> context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        TopComponent component = WindowManager.getDefault()
                .findTopComponent("CodexTopComponent");

        if (component instanceof CodexTopComponent codex) {
            codex.open();
            codex.addAttachments(context);
            codex.requestActive();
        }
    }
}
