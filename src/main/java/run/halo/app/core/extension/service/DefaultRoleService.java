package run.halo.app.core.extension.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.RoleBinding;
import run.halo.app.core.extension.RoleBinding.RoleRef;
import run.halo.app.core.extension.RoleBinding.Subject;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;

/**
 * @author guqing
 * @since 2.0.0
 */
@Slf4j
@Service
public class DefaultRoleService implements RoleService {

    private final ReactiveExtensionClient extensionClient;

    public DefaultRoleService(ReactiveExtensionClient extensionClient) {
        this.extensionClient = extensionClient;
    }

    @Override
    @NonNull
    public Role getRole(@NonNull String name) {
        return extensionClient.fetch(Role.class, name).blockOptional().orElseThrow();
    }

    @Override
    public Mono<Role> getMonoRole(String name) {
        return extensionClient.get(Role.class, name);
    }

    @Override
    public Flux<RoleRef> listRoleRefs(Subject subject) {
        return extensionClient.list(RoleBinding.class,
                binding -> binding.getSubjects().contains(subject),
                null)
            .map(RoleBinding::getRoleRef);
    }

    @Override
    @NonNull
    public List<Role> listDependencies(Set<String> names) {
        List<Role> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(names);
        while (!queue.isEmpty()) {
            String roleName = queue.poll();
            // detecting cycle in role dependencies
            if (visited.contains(roleName)) {
                log.warn("Detected a cycle in role dependencies: {},and skipped automatically",
                    roleName);
                continue;
            }
            visited.add(roleName);
            extensionClient.fetch(Role.class, roleName)
                .blockOptional()
                .ifPresent(role -> {
                    result.add(role);
                    Map<String, String> annotations = role.getMetadata().getAnnotations();
                    if (annotations != null) {
                        String roleNameDependencies = annotations.get(Role.ROLE_DEPENDENCIES_ANNO);
                        List<String> roleDependencies = stringToList(roleNameDependencies);
                        queue.addAll(roleDependencies);
                    }
                });
        }
        return result;
    }

    @Override
    public Flux<Role> list(Set<String> roleNames) {
        return Flux.fromIterable(ObjectUtils.defaultIfNull(roleNames, Set.of()))
            .flatMap(roleName -> extensionClient.fetch(Role.class, roleName));
    }

    @NonNull
    private List<String> stringToList(String str) {
        if (StringUtils.isBlank(str)) {
            return Collections.emptyList();
        }
        return JsonUtils.jsonToObject(str,
            new TypeReference<>() {
            });
    }
}
