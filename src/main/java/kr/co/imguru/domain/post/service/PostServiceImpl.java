package kr.co.imguru.domain.post.service;

import jakarta.transaction.Transactional;
import kr.co.imguru.domain.like.LikePost;
import kr.co.imguru.domain.like.repository.LikePostRepository;
import kr.co.imguru.domain.like.repository.LikePostSearchRepository;
import kr.co.imguru.domain.member.entity.Member;
import kr.co.imguru.domain.member.repository.MemberRepository;
import kr.co.imguru.domain.post.dto.PostCreateDto;
import kr.co.imguru.domain.post.dto.PostReadDto;
import kr.co.imguru.domain.post.dto.PostUpdateDto;
import kr.co.imguru.domain.post.entity.Post;
import kr.co.imguru.domain.post.repository.PostRepository;
import kr.co.imguru.domain.post.repository.PostSearchRepository;
import kr.co.imguru.global.common.PostCategory;
import kr.co.imguru.global.common.Role;
import kr.co.imguru.global.exception.DuplicatedException;
import kr.co.imguru.global.exception.ForbiddenException;
import kr.co.imguru.global.exception.IllegalArgumentException;
import kr.co.imguru.global.exception.NotFoundException;
import kr.co.imguru.global.model.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;

    private final MemberRepository memberRepository;

    private final LikePostRepository likePostRepository;

    private final PostSearchRepository postSearchRepository;

    private final LikePostSearchRepository likePostSearchRepository;

    private final RedisTemplate<String, Object> redisTemplate; // RedisTemplate 주입

    @Override
    @Transactional
    public void createPost(PostCreateDto createDto) {
        Optional<Member> member = memberRepository.findByNicknameAndIsDeleteFalse(createDto.getMemberNickname());

        isMember(member);

        isPostCategory(createDto.getCategoryName());

        postRepository.save(toEntity(member.get().getRole(), createDto));
    }

    @Override
    @Transactional
    public PostReadDto getPost(Long postId) {
        Optional<Post> post = postRepository.findByIdAndIsDeleteFalse(postId);

        isPost(post);

        updateCntToRedis(postId, "views");

        return toReadDto(post.get());
    }

    @Override
    @Transactional
    public List<PostReadDto> getAllGuruPosts() {
        return postRepository.findAllByIsGuruAndIsDeleteFalse(true)
                .stream()
                .map(this::toReadDto)
                .toList();
    }

    @Override
    @Transactional
    public List<PostReadDto> getAllPosts() {
        return postRepository.findAllByIsDeleteFalse()
                .stream()
                .map(this::toReadDto)
                .toList();
    }

    @Override
    @Transactional
    public List<PostReadDto> getPostsByMember(String memberNickname) {
        return postSearchRepository.findPostsByMemberNickname(memberNickname)
                .stream()
                .map(this::toReadDto)
                .toList();
    }

    @Override
    @Transactional
    public PostReadDto addLikePostByMemberNickname(Long postId, String memberNickname) {
        Optional<Member> member = memberRepository.findByNicknameAndIsDeleteFalse(memberNickname);
        isMember(member);

        Optional<Post> post = postRepository.findByIdAndIsDeleteFalse(postId);
        isPost(post);

        isLikePostDuplicated(postId, member.get().getId());

        LikePost create = LikePost.builder()
                .member(member.get())
                .post(post.get())
                .build();

        likePostRepository.save(create);

        post.get().addLikeCnt();

        postRepository.save(post.get());

        return toReadDto(post.get());
    }

    @Override
    @Transactional
    public List<PostReadDto> getLikePostsByMember(String memberNickname) {
        return postSearchRepository.findLikePostsByMemberNickname(memberNickname)
                .stream()
                .map(this::toReadDto)
                .toList();
    }

    @Override
    @Transactional
    public PostReadDto updatePost(Long postId, PostUpdateDto updateDto) {
        Optional<Member> member = memberRepository.findByNicknameAndIsDeleteFalse(updateDto.getMemberNickname());
        isMember(member);

        Optional<Post> post = postRepository.findByIdAndIsDeleteFalse(postId);
        isPost(post);

        isWriter(member, post);

        isPostCategory(updateDto.getCategoryName());
        post.get().changePost(updateDto);

        postRepository.save(post.get());

        return toReadDto(post.get());
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        Optional<Post> post = postRepository.findByIdAndIsDeleteFalse(postId);

        isPost(post);

        post.get().changeDeleteAt();

        postRepository.save(post.get());
    }


    /*
    게시글 상세조회 요청 시, 해당 postId에 해당하는 viewCnt를 +1 해준 값을 Redis에 저장
     */
    @Override
    @Transactional
    public void updateCntToRedis(Long postId, String hashKey) {
        HashOperations<String, String, Object> hashOperations = redisTemplate.opsForHash();

        String key = "postId::" + postId;

        if (hashOperations.get(key, hashKey) == null) {
            if (hashKey.equals("views")) {
                hashOperations.put(key, hashKey, postRepository.findByIdAndIsDeleteFalse(postId).get().getViewCnt());
            }
            hashOperations.increment(key, hashKey, 1L);
            System.out.println("hashOperations.get is null ---- " + hashOperations.get(key, hashKey));
        } else {
            hashOperations.increment(key, hashKey, 1L);
            System.out.println("hashOperations.get is not null ---- " + hashOperations.get(key, hashKey));
        }
    }

    /*
   Redis에 기록된 정보들을 DB에 업데이트를 진행하면서 데이터의 일관성을 유지하고, Redis의 저장된 정보들을 초기화
   Spring Scheduled를 사용하여 일정 시간마다 실행이 되도록 설정
    */
    @Transactional
    @Scheduled(fixedDelay = 1000L * 180L) // 180초
    public void deleteCntToRedis() {
        String viewKey = "views";
        Set<String> redisKey = redisTemplate.keys("postId*");
        Iterator<String> it = redisKey.iterator();

        while (it.hasNext()) {
            String data = it.next();
            Long postId = Long.parseLong(data.split("::")[1]);

            if (redisTemplate.opsForHash().get(data, viewKey) == null) {
                break;
            } else {
                Long viewCnt = Long.parseLong(String.valueOf(redisTemplate.opsForHash().get(data, viewKey)));
                addViewCntFromRedis(postId, viewCnt);
                redisTemplate.opsForHash().delete(data, viewKey);
            }
        }
        System.out.println("Update Complete From Redis");
    }

    private void addViewCntFromRedis(Long postId, Long viewCnt) {
        Optional<Post> post = postRepository.findByIdAndIsDeleteFalse(postId);

        if (!post.isEmpty()) {
            post.get().addViewCnt(viewCnt);

            postRepository.save(post.get());
        }
    }

    private void isMember(Optional<Member> member) {
        if (member.isEmpty()) {
            throw new NotFoundException(ResponseStatus.FAIL_MEMBER_NOT_FOUND);
        }
    }

    private void isPost(Optional<Post> post) {
        if (post.isEmpty()) {
            throw new NotFoundException(ResponseStatus.FAIL_POST_NOT_FOUND);
        }
    }

    private void isWriter(Optional<Member> member, Optional<Post> post) {
        if (!member.get().getNickname().equals(post.get().getMember().getNickname())) {
            throw new ForbiddenException(ResponseStatus.FAIL_POST_WRITER_NOT_MATCH);
        }
    }

    private void isPostCategory(String categoryName) {
        try {
            PostCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ResponseStatus.FAIL_POST_CATEGORY_NOT_FOUND);
        }
    }

    private void isLikePostDuplicated(Long postId, Long memberId) {
        Optional<LikePost> likePost = Optional.ofNullable(likePostSearchRepository.existsByPostIdAndMemberId(postId, memberId));

        if (likePost.isPresent()) {
            throw new DuplicatedException(ResponseStatus.FAIL_POST_LIKE_DUPLICATED);
        }
    }

    private Post toEntity(Role role, PostCreateDto dto) {
        if (role.getUserRole().equals("도사")) {
            return Post.builder()
                    .member(memberRepository.findByNicknameAndIsDeleteFalse(dto.getMemberNickname()).get())
                    .postCategory(PostCategory.valueOf(dto.getCategoryName()))
                    .title(dto.getTitle())
                    .content(dto.getContent())
                    .price(dto.getPrice())
                    .isGuru(true)
                    .likeCnt(0L)
                    .viewCnt(0L)
                    .build();
        } else {
            return Post.builder()
                    .member(memberRepository.findByNicknameAndIsDeleteFalse(dto.getMemberNickname()).get())
                    .postCategory(PostCategory.valueOf(dto.getCategoryName()))
                    .title(dto.getTitle())
                    .content(dto.getContent())
                    .price("0")
                    .isGuru(false)
                    .likeCnt(0L)
                    .viewCnt(0L)
                    .build();
        }
    }

    private PostReadDto toReadDto(Post post) {
        return PostReadDto.builder()
                .postId(post.getId())
                .memberNickname(post.getMember().getNickname())
                .postCategory(String.valueOf(post.getPostCategory()))
                .title(post.getTitle())
                .content(post.getContent())
                .price(post.getPrice())
                .isGuru(post.isGuru())
                .viewCnt(post.getViewCnt())
                .likeCnt(post.getLikeCnt())
                .build();
    }

}
