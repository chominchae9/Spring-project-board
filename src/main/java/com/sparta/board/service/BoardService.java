package com.sparta.board.service;

import com.sparta.board.common.ApiResponseDto;
import com.sparta.board.common.ResponseUtils;
import com.sparta.board.common.SuccessResponse;
import com.sparta.board.dto.BoardRequestsDto;
import com.sparta.board.dto.BoardResponseDto;
import com.sparta.board.dto.CommentResponseDto;
import com.sparta.board.entity.Board;
import com.sparta.board.entity.Comment;
import com.sparta.board.entity.User;
import com.sparta.board.entity.enumSet.ErrorType;
import com.sparta.board.entity.enumSet.UserRoleEnum;
import com.sparta.board.exception.RestApiException;
import com.sparta.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    // 게시글 전체 목록 조회
    @Transactional(readOnly = true)
    public ApiResponseDto<List<BoardResponseDto>> getPosts() {

        List<Board> boardList = boardRepository.findAllByOrderByModifiedAtDesc(); //db에서 게시글을 modifiedAt 내림차순으로 가져옴
        List<BoardResponseDto> responseDtoList = new ArrayList<>(); // 최종 반환할 DTO 리스트 준비

        for (Board board : boardList) { // 게시글 각각에 대해 댓글 가공 -> DTO 변환 수행
            // 댓글리스트 작성일자 기준 내림차순 정렬
            board.getCommentList()
                    .sort(Comparator.comparing(Comment::getModifiedAt)
                            .reversed());

            // 대댓글은 제외 부분 작성
            List<CommentResponseDto> commentList = new ArrayList<>(); // 최종적으로 응답에 담을 댓글 DTO 리스트를 준비
            for (Comment comment : board.getCommentList()) { // 해당 board에 달린 전체 댓그을 하나씩 꺼냄
                if (comment.getParentCommentId() == null) { // 부모가 없는 , 즉 원댓글일 경우
                    commentList.add(CommentResponseDto.from(comment)); // 선택된 댓글을 CommentResponseDto 로 변환해 새 리스트에 담음
                }
            }

            // List<BoardResponseDto> 로 만들기 위해 board와 commentList를 넣어 BoardResponseDto 로 만들고, list 에 dto 를 하나씩 넣는다.
            responseDtoList.add(BoardResponseDto.from(board, commentList));
        }
        return ResponseUtils.ok(responseDtoList); //ApiResponseDto 형태로
    }

    // 게시글 작성
    @Transactional
    public ApiResponseDto<BoardResponseDto> createPost(BoardRequestsDto requestsDto, User user) {

        // 작성 글 추출
        Board board = boardRepository.save(Board.of(requestsDto, user));

        // BoardResponseDto 로 변환 후 responseEntity body 에 담아 반환
        return ResponseUtils.ok(BoardResponseDto.from(board));
    }

    // 선택된 게시글 조회
    @Transactional(readOnly = true)
    public ApiResponseDto<BoardResponseDto> getPost(Long id) {
        // Id에 해당하는 게시글이 있는지 확인
        Optional<Board> board = boardRepository.findById(id);
        if (board.isEmpty()) { // 해당 게시글이 없다면
            throw new RestApiException(ErrorType.NOT_FOUND_WRITING);
        }

        // 댓글리스트 작성일자 기준 내림차순 정렬
        board.get()
                .getCommentList()
                .sort(Comparator.comparing(Comment::getModifiedAt)
                        .reversed());

        // 대댓글은 제외 부분 작성
        List<CommentResponseDto> commentList = new ArrayList<>();
        for (Comment comment : board.get().getCommentList()) {
            if (comment.getParentCommentId() == null) {
                commentList.add(CommentResponseDto.from(comment));
            }
        }

        // board 를 responseDto 로 변환 후, ResponseEntity body 에 dto 담아 리턴
        return ResponseUtils.ok(BoardResponseDto.from(board.get(), commentList));
    }

    // 선택된 게시글 수정
    @Transactional
    public ApiResponseDto<BoardResponseDto> updatePost(Long id, BoardRequestsDto requestsDto, User user) {

        // 선택한 게시글이 DB에 있는지 확인
        Optional<Board> board = boardRepository.findById(id);
        if (board.isEmpty()) {
            throw new RestApiException(ErrorType.NOT_FOUND_WRITING);
        }

        // 선택한 게시글의 작성자와 토큰에서 가져온 사용자 정보가 일치하는지 확인 (수정하려는 사용자가 관리자라면 게시글 수정 가능)
        Optional<Board> found = boardRepository.findByIdAndUser(id, user);
        if (found.isEmpty() && user.getRole() == UserRoleEnum.USER) { // found가 비어있다면 , 즉 id와 user가 매칭이 안 된다면
            throw new RestApiException(ErrorType.NOT_WRITER); // 권한 없음 예외 발생
        }

        // 게시글 id 와 사용자 정보 일치한다면, 게시글 수정
        board.get().update(requestsDto, user);
        // update()로 글 내용을 바꾸면 modifiedAt이 새로 바뀜(메모리 안에서만!)
        // -> flush 해주어야 db에 즉시 반영되고 dto로 꺼낼 때 최신 수정일(modifiedAt())을 볼 수 있다.
        boardRepository.flush();

        return ResponseUtils.ok(BoardResponseDto.from(board.get()));
    }

    // 게시글 삭제
    @Transactional
    public ApiResponseDto<SuccessResponse> deletePost(Long id, User user) {

        // 선택한 게시글이 DB에 있는지 확인
        Optional<Board> found = boardRepository.findById(id);
        if (found.isEmpty()) {
            throw new RestApiException(ErrorType.NOT_FOUND_WRITING);
        }

        // 선택한 게시글의 작성자와 토큰에서 가져온 사용자 정보가 일치하는지 확인 (삭제하려는 사용자가 관리자라면 게시글 삭제 가능)
        Optional<Board> board = boardRepository.findByIdAndUser(id, user);
        if (board.isEmpty() && user.getRole() == UserRoleEnum.USER) { // 일치하는 게시물이 없다면
            throw new RestApiException(ErrorType.NOT_WRITER);
        }

        // 게시글 id 와 사용자 정보 일치한다면, 게시글 수정
        boardRepository.deleteById(id);
        return ResponseUtils.ok(SuccessResponse.of(HttpStatus.OK, "게시글 삭제 성공"));

    }

}
