package com.gzsll.hupu.presenter;

import android.text.TextUtils;

import com.gzsll.hupu.api.forum.ForumApi;
import com.gzsll.hupu.bean.Forums;
import com.gzsll.hupu.bean.ForumsData;
import com.gzsll.hupu.bean.ForumsResult;
import com.gzsll.hupu.bean.MyForumsData;
import com.gzsll.hupu.bean.MyForumsResult;
import com.gzsll.hupu.db.Forum;
import com.gzsll.hupu.db.ForumDao;
import com.gzsll.hupu.ui.view.ForumListView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by sll on 2016/3/11.
 */
public class ForumListPresenter extends Presenter<ForumListView> {

    @Inject
    ForumDao mForumDao;
    @Inject
    ForumApi mForumApi;


    @Singleton
    @Inject
    public ForumListPresenter() {
    }


    private List<Forum> forums = new ArrayList<>();
    private String forumId;


    public void onForumListReceive(final String forumId) {
        view.showLoading();
        Observable.just(forumId).subscribeOn(Schedulers.io()).map(new Func1<String, List<Forum>>() {
            @Override
            public List<Forum> call(String s) {
                return mForumDao.queryBuilder().where(ForumDao.Properties.ForumId.eq(forumId)).list();
            }
        }).flatMap(new Func1<List<Forum>, Observable<List<Forum>>>() {
            @Override
            public Observable<List<Forum>> call(List<Forum> fora) {
                if (fora.isEmpty()) {
                    return loadFromNet(forumId);
                } else {
                    return Observable.just(fora);
                }
            }
        }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<List<Forum>>() {
            @Override
            public void call(List<Forum> fora) {
                view.hideLoading();
                view.renderForumList(fora);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                view.onError();
            }
        });
    }


    private Observable<List<Forum>> loadFromNet(String forumId) {
        return Observable.just(forumId).flatMap(new Func1<String, Observable<List<Forum>>>() {
            @Override
            public Observable<List<Forum>> call(String s) {
                if (TextUtils.equals(s, "0")) {
                    return loadUserForums();
                } else {
                    return loadAllForums(s);
                }
            }
        });
    }

    private Observable<List<Forum>> loadUserForums() {
        return mForumApi.getMyForums().flatMap(new Func1<MyForumsResult, Observable<List<Forum>>>() {
            @Override
            public Observable<List<Forum>> call(MyForumsResult result) {
                if (result != null && result.data != null) {
                    MyForumsData data = result.data;
                    for (Forum forum : data.sub) {
                        forum.setForumId(data.fid);
                        forum.setCategoryName(data.name);
                        forum.setWeight(1);
                    }
                    return Observable.just(data.sub);
                }

                return null;
            }
        }).doOnNext(new Action1<List<Forum>>() {
            @Override
            public void call(List<Forum> fora) {
                saveToDb(fora);
            }
        });
    }

    private Observable<List<Forum>> loadAllForums(final String forumId) {
        return mForumApi.getForums().flatMap(new Func1<ForumsResult, Observable<List<Forum>>>() {
            @Override
            public Observable<List<Forum>> call(ForumsResult result) {
                if (result != null) {
                    for (ForumsData data : result.data) {
                        if (data.fid.equals(forumId)) {
                            List<Forum> forumList = new ArrayList<>();
                            for (Forums forums : data.sub) {
                                for (Forum forum : forums.data) {
                                    forum.setForumId(data.fid);
                                    forum.setCategoryName(forums.name);
                                    forum.setWeight(forums.weight);
                                    forumList.add(forum);
                                }
                            }
                            return Observable.just(forumList);
                        }
                    }

                }
                return null;
            }
        }).doOnNext(new Action1<List<Forum>>() {
            @Override
            public void call(List<Forum> fora) {
                saveToDb(fora);
            }
        });

    }


    private void saveToDb(List<Forum> forums) {
        for (Forum forum : forums) {
            List<Forum> forumList = mForumDao.queryBuilder().where(ForumDao.Properties.ForumId.eq(forum.getForumId()), ForumDao.Properties.Fid.eq(forum.getFid())).list();
            if (!forumList.isEmpty()) {
                forum.setId(forumList.get(0).getId());
            }
            mForumDao.insertOrReplace(forum);
        }
    }


    @Override
    public void detachView() {

    }
}
