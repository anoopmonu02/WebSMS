package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Shared helpers of the TC controllers: the "feature is on for this school" gate and the logged-in user.
 */
abstract class TcBaseController extends BaseController {

    private final TcConfigService tcConfigService;
    private final UserService userService;

    protected TcBaseController(TcConfigService tcConfigService, UserService userService) {
        this.tcConfigService = tcConfigService;
        this.userService = userService;
    }

    /** Returns a redirect when the page must not open (no school, or the feature is off); null when it may. */
    protected String featureBlock(School school, RedirectAttributes ra) {
        String message = featureMessage(school);
        if (message == null) {
            return null;
        }
        ra.addFlashAttribute("error", message);
        return "redirect:/dashboard";
    }

    /** Null when the feature is usable for the school, otherwise the message to show. */
    protected String featureMessage(School school) {
        if (school == null) {
            return "No school is selected for this login.";
        }
        if (!tcConfigService.isEnabled(school)) {
            return "The Transfer Certificate feature is not enabled for this school. Turn it on in App Config, TC Settings.";
        }
        return null;
    }

    protected UserEntity currentUser() {
        UserEntity user = userService.getLoggedInUser();
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
        return user;
    }
}
