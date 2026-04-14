package it.unicas.spring.restreactapplication;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private static final int PAGE_SIZE = 5;

    private final FriendRepository friendRepository;

    public FriendController(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;
    }

    @GetMapping
    public Page<Friend> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("name"));

        if (q.isBlank()) {
            return friendRepository.findAll(pageable);
        }
        return friendRepository.search(q, pageable);
    }

    @GetMapping("/{id}")
    public Friend findById(@PathVariable Long id) {
        return friendRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend not found"));
    }

    @PostMapping
    public ResponseEntity<Friend> create(@RequestBody Friend friend) {
        Friend saved = friendRepository.save(friend);
        return ResponseEntity
                .created(URI.create("/api/v1/friends/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public Friend update(@PathVariable Long id, @RequestBody Friend friend) {
        Friend current = friendRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend not found"));

        current.setName(friend.getName());
        current.setEmail(friend.getEmail());
        current.setPhone(friend.getPhone());

        return friendRepository.save(current);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!friendRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Friend not found");
        }
        friendRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
