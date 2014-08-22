/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xjt.newpic.filtershow.editors;

import android.content.Context;
import android.widget.FrameLayout;

import com.xjt.newpic.R;
import com.xjt.newpic.filtershow.imageshow.ImageShow;

/**
 * The editor with no slider for filters without UI
 */
public class ImageOnlyEditor extends Editor {
    public final static int ID = R.id.imageOnlyEditor;
    private final String LOGTAG = "ImageOnlyEditor";

    public ImageOnlyEditor() {
        super(ID);
    }

    protected ImageOnlyEditor(int id) {
        super(id);
    }

    public boolean useUtilityPanel() {
        return false;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        mView = mImageShow = new ImageShow(context);
    }

}