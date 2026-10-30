package com.suraj.netbeans.codex;

import java.beans.BeanDescriptor;
import java.beans.SimpleBeanInfo;

/** The form designer places this bean as one component; its layout is internal. */
public final class CodexChatPanelBeanInfo extends SimpleBeanInfo {
    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor descriptor = new BeanDescriptor(CodexChatPanel.class);
        descriptor.setValue("isContainer", Boolean.FALSE);
        return descriptor;
    }
}
