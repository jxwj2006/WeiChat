package org.liyou.qixiaobo.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.liyou.qixiaobo.daos.HeroDao;
import org.liyou.qixiaobo.daos.SkillDao;
import org.liyou.qixiaobo.entities.hibernate.Hero;
import org.liyou.qixiaobo.entities.hibernate.Skill;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 14-3-3.
 */
@Component
public class DotaService implements ApplicationListener<ContextRefreshedEvent> {
    public static final String dota_website_url = "http://dota.db.766.com/index.php/index/";
    public static final String dota_heroes_url = dota_website_url + "herolist/0/0/";
    static boolean complete = false;
    static boolean needUpdate = false;
    @Resource
    private SkillDao skillDao;
    @Resource
    private HeroDao heroDao;


    public List<Hero> searchHeros (String name) {
        if (!complete || name == null || name.trim ().equals ("")) {
            return null;
        }
        List<Hero> heros = heroDao.queryByHeroNameOrShortname (name);

        return heros;
    }

    private void initModel () {
        if (!needUpdate && complete) {
            //that means complete or running
            return;
        }
        complete = false;
        if (!needUpdate) {
            if (heroDao.queryHeroNums () > 100) {
                complete = true;
                return;
            }
        }
        for (int i = 1; i <= 6; i++) {
            try {
                Document doc = Jsoup.connect (dota_heroes_url + i).timeout (0).get ();
                Elements items = doc.select (".container");//dota info
                if (items != null && items.size () != 0) {
                    Element container = items.get (0);
                    Elements tables = container.getElementsByTag ("table");
                    if (tables != null && tables.size () != 0) {
                        Element table = tables.get (0);
                        Elements rows = table.getElementsByTag ("tr");
                        if (rows != null) {
                            for (Element row : rows) {
                                Elements tds = row.getElementsByTag ("td");
                                if (tds == null || tds.size () == 0) {
                                    continue;
                                }
                                Element icon = tds.get (0);
                                Element name = tds.get (1);
                                Element shortName = tds.get (2);
                                Element skills = tds.get (4);
                                Hero hero = new Hero ();
                                hero.setUrl (icon.child (0).attr ("href"));
                                hero.setImgUrl (icon.child (0).child (0).attr ("src"));
                                hero.setName (name.text ());
                                hero.setShortName (shortName.text ());
                                Document docTmp = Jsoup.connect (hero.getUrl ()).timeout (0).get ();
                                Elements eles = docTmp.getElementsByClass ("data");
                                if (eles != null && eles.size () != 0) {
                                    Element element = eles.get (0);
                                    String des = "";
                                    try {
                                        des = element.getElementsByTag ("p").get (0).text ();
                                    } catch (Exception ex) {

                                    }
                                    hero.setDes (des);
                                }
                                List<Skill> skillList = new ArrayList<Skill> (skills.childNodeSize ());
                                for (Element skillElement : skills.children ()) {
                                    Skill skill = new Skill ();
                                    skill.setSkillUrl (skillElement.attr ("href"));
                                    skill.setSkillName (skillElement.attr ("title"));
                                    skill.setSkillImgUrl (skillElement.child (0).attr ("src"));
                                    docTmp = Jsoup.connect (skill.getSkillUrl ()).timeout (0).get ();
                                    eles = docTmp.getElementsByClass ("data");
                                    if (eles != null && eles.size () != 0) {
                                        Element element = eles.get (0);
                                        String des = "";
                                        try {
                                            des = element.getElementsByTag ("p").get (0).text ();
                                        } catch (Exception ex) {

                                        }
                                        skill.setSkillDesc (des);
                                    }
                                    Skill skillTemp = skillDao.queryBySkillName (skill.getSkillName ());
                                    if (skillTemp != null) {
                                        if (needUpdate) {
                                            skill = skillDao.update (skillTemp);
                                        }
                                    } else {
                                        skill = skillDao.insert (skill);
                                    }
                                    skillList.add (skill);
                                }
                                hero.setSkills (skillList);
                                Hero heroTemp = heroDao.queryByHeroName (hero.getName ());
                                if (heroTemp != null) {
                                    if (needUpdate) {
                                        hero = heroDao.update (hero);
                                    }
                                } else {
                                    hero = heroDao.insert (hero);
                                }

                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace ();
            }
        }
        complete = true;
    }

    @Override
    public void onApplicationEvent (ContextRefreshedEvent contextRefreshedEvent) {
        if (contextRefreshedEvent.getApplicationContext ().getParent () == null) {//root application context
            new Thread (new Runnable () {
                @Override
                public void run () {
                    initModel ();
                }
            }).start ();
        }

    }
}
