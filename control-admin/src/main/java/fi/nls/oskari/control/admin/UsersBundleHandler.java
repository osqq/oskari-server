package fi.nls.oskari.control.admin;

import fi.nls.oskari.annotation.OskariViewModifier;
import fi.nls.oskari.control.view.modifier.bundle.BundleHandler;
import fi.nls.oskari.util.JSONHelper;
import fi.nls.oskari.view.modifier.ModifierException;
import fi.nls.oskari.view.modifier.ModifierParams;
import org.json.JSONObject;
import org.oskari.user.util.UserHelper;


/**
 * Injects password requirements to admin-users config

 {
     "requirements": {
        "length": 8,
        "case": true
     }
 }
 */
@OskariViewModifier("admin-users")
public class UsersBundleHandler extends BundleHandler {

    public boolean modifyBundle(final ModifierParams params) throws ModifierException {
        final JSONObject config = getBundleConfig(params.getConfig());
        JSONHelper.putValue(config, "requirements", UserHelper.getPasswordRequirements());
        return false;
    }
}
