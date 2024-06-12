package com.xhpolaris.meowpick.infrastructure.repository;

import com.xhpolaris.meowpick.common.utils.ScoreTransfor;
import com.xhpolaris.meowpick.domain.service.Context;
import com.xhpolaris.meowpick.common.PageEntity;
import com.xhpolaris.meowpick.domain.model.valobj.CommentCmd;
import com.xhpolaris.meowpick.domain.model.valobj.CommentVO;
import com.xhpolaris.meowpick.domain.model.valobj.ReplyVO;
import com.xhpolaris.meowpick.domain.repository.ICommentRepository;
import com.xhpolaris.meowpick.infrastructure.dao.CommentDao;
import com.xhpolaris.meowpick.infrastructure.mapstruct.CommentMap;
import com.xhpolaris.meowpick.infrastructure.pojo.CommentCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentRepository implements ICommentRepository {
    private final CommentDao commentDao;
    private final Context    context;

    @Override
    public CommentVO add(CommentCmd.CreateCmd cmd) {
        CommentCollection db = CommentMap.instance.cmd2db(cmd);
        db.setUid(context.uid());
        commentDao.save(db);
        return CommentMap.instance.db2vo(db);
    }

    @Override
    public CommentVO del(String id) {
        CommentCollection db = commentDao.findById(id).orElse(null);
        if (db == null) {
            return null;
        }

        commentDao.deleteById(id);

        return CommentMap.instance.db2vo(db);
    }

    @Override
    public CommentVO update(CommentCmd.UpdateCmd cmd) {
        CommentCollection db = commentDao.findById(cmd.getId()).orElse(null);
        if (db == null) {
            return null;
        }

        commentDao.save(CommentMap.instance.cmd2db(cmd));

        return CommentMap.instance.db2vo(db);
    }

    @Override
    public PageEntity<CommentVO> query(CommentCmd.Query query) {
        Page<CommentCollection> page = commentDao.findAllByTargetOrderByCrateAtDesc(query.getId(),
                PageRequest.of(query.getPage(),
                        query.getSize()
                              ));

        return BasicRepository.page(page, c -> {
            CommentVO vo = CommentMap.instance.db2vo(c);
            vo.setCrateAt(c.getCrateAt());
            vo.setReply(Optional.ofNullable(c.getReplies())
                                .orElse(Collections.emptyList())
                                .size());

            return vo;
        });
    }

    @Override
    public ReplyVO find(String id) {
        CommentCollection db = commentDao.findById(id).orElse(null);
        if (db == null) {
            return null;
        }

        ReplyVO vo = CommentMap.instance.db2reply(db);
        vo.setReplies(db.getReplies()
                        .stream()
                        .map(CommentMap.instance::db2reply)
                        .toList());

        return vo;
    }

    @Override
    public PageEntity<CommentVO> queryUserComment(CommentCmd.History query) {
        var uids = query.getUid().split(",");
        if (uids.length > 1) {
            return BasicRepository.page(
                    commentDao.findAllByUidOrderByCrateAt(uids[0], uids[1],
                            PageRequest.of(query.getPage(), query.getSize())),
                    CommentMap.instance::db2vo
            );
        }
        return BasicRepository.page(
                commentDao.findAllByUidOrderByCrateAt(query.getUid(),
                        PageRequest.of(query.getPage(), query.getSize())),
                CommentMap.instance::db2vo
        );
    }

    @Override
    public ScoreTransfor.Score score(String id) {
        List<CommentCollection> commentList = commentDao.findAllByTargetOrderByCrateAt(id);
        if (CollectionUtils.isEmpty(commentList)) {
            return new ScoreTransfor.Score();
        }

        return ScoreTransfor.transfor(commentList.stream().map(CommentCollection::getScore).filter(Objects::nonNull).toList());
    }

    @Override
    public Map<String, List<Integer>> scoreIn(List<String> list) {
        list = list.stream().filter(Objects::nonNull).toList();
        if (CollectionUtils.isEmpty(list)) {
            return Map.of();
        }
        List<CommentCollection> commentList = commentDao.findAllByTargetInOrderByCrateAt(list);
        if (CollectionUtils.isEmpty(commentList)) {
            return new HashMap<>();
        }
        Map<String, List<CommentCollection>> groupByTarget = commentList.stream()
                                                                        .collect(Collectors.groupingBy(CommentCollection::getTarget));
        return groupByTarget.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    item -> item.getValue().stream().map(CommentCollection::getScore).toList()));
    }
}
