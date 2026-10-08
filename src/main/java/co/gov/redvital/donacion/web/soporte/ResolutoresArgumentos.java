package co.gov.redvital.donacion.web.soporte;

import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Inyecta en los controladores el {@link Actor} y el {@link ContextoPeticion}.
 * Un parámetro {@code Actor} anotado con {@link Opcional} admite peticiones anónimas.
 */
@Configuration
class ResolutoresArgumentos implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
        resolutores.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parametro) {
                return parametro.getParameterType() == Actor.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
                                          NativeWebRequest peticion, WebDataBinderFactory fabrica) {
                Actor actor = ResolutorActor.actual();
                if (actor == null && !parametro.hasParameterAnnotation(Opcional.class)) {
                    throw new OperacionNoPermitida();
                }
                return actor;
            }
        });
        resolutores.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parametro) {
                return parametro.getParameterType() == ContextoPeticion.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
                                          NativeWebRequest peticion, WebDataBinderFactory fabrica) {
                return FiltroCorrelacion.contexto(peticion.getNativeRequest(HttpServletRequest.class));
            }
        });
    }
}
