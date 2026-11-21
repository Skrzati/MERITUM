package pl.gov.kprm.oceny.auth;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import pl.gov.kprm.oceny.domain.User;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter p) {
        return p.hasParameterAnnotation(CurrentUser.class) && User.class.isAssignableFrom(p.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer mav, NativeWebRequest req,
                                  WebDataBinderFactory binder) {
        Object user = req.getAttribute(CurrentUserFilter.ATTR, RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wymagane uwierzytelnienie (Bearer JWT lub " + CurrentUserFilter.DEMO_HEADER + ")");
        }
        return user;
    }
}
