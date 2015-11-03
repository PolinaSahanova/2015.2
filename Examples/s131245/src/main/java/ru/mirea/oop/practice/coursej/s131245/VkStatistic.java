package ru.mirea.oop.practice.coursej.s131245;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.oop.practice.coursej.api.vk.FriendsApi;
import ru.mirea.oop.practice.coursej.api.vk.MessagesApi;
import ru.mirea.oop.practice.coursej.api.vk.entities.Contact;
import ru.mirea.oop.practice.coursej.impl.vk.ext.ServiceBotsExtension;

import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Created by aleksejpluhin on 13.10.15.
 */
public final class VkStatistic extends ServiceBotsExtension {
    private static final Logger logger = LoggerFactory.getLogger(VkStatistic.class);
    private static final  Map<Long, ArrayList<Session>> MapSession = new HashMap<>();
    private static final Map<Long, Contact> friendsMap = new HashMap<>();
    private final MessagesApi msgApi;
    boolean alreadySend = false;
    private static final String FRIENDS_FIELDS = "nickname, " +
            "domain, " +
            "sex, " +
            "bdate, " +
            "city, " +
            "country, " +
            "timezone, " +
            "photo_50, " +
            "photo_100, " +
            "photo_200_orig, " +
            "has_mobile, " +
            "contacts, " +
            "education, " +
            "online, " +
            "relation, " +
            "last_seen, " +
            "status, " +
            "can_write_private_message, " +
            "can_see_all_posts, " +
            "can_post, " +
            "universities";

    static {
        MapSession.clear();
    }

    public VkStatistic() throws Exception {
        super("vk.services.VkStatistic");
        this.msgApi = api.getMessages();
    }


    @Override
    protected void doEvent(Event event) {
        SimpleDateFormat dt = new SimpleDateFormat("HH:mm");

        if(MapSession.isEmpty()) {
            firstPutOfFriends();
        }

        switch (event.type) {

            case FRIEND_ONLINE: {
                UserOnline userOnline = (UserOnline) event.object;
                Long key = userOnline.getContact().id;

                Session session = new Session(new Date());
                if (MapSession.get(key).isEmpty()) {
                    MapSession.get(key).add(session);
                } else {
                    try {
                        int index = MapSession.get(key).size() - 1;
                        if (MapSession.get(key).get(index).getEnd() == null) {
                            MapSession.get(key).remove(index);
                            MapSession.get(key).add(session);
                        } else {
                            throw new Exception();
                        }
                    } catch (Exception e) {
                        MapSession.get(key).add(session);
                    }

                }
                logger.debug(event.object + " вошел в " + dt.format(MapSession.get(key).get(MapSession.get(key).size() - 1).getBegin()));

                break;

            }

            case FRIEND_OFFLINE: {
                UserOffline userOffline = (UserOffline) event.object;
                Long key = userOffline.getContact().id;
                int index = MapSession.get(key).size() - 1;
                String name = ((UserOffline) event.object).getContact().firstName + " " + ((UserOffline) event.object).getContact().lastName;
                if (MapSession.get(key).get(index).getBegin() == null) {
                        break;
                    }
                MapSession.get(key).get(index).setEnd(new Date());
                logger.debug(name + " вышел в " + dt.format(MapSession.get(key).get(MapSession.get(key).size() - 1).getEnd()));
                break;
            }


            case MESSAGE_RECEIVE: {
                if (alreadySend) {
                    alreadySend = false;
                    break;
                }
                Message msg = (Message) event.object;
                Contact contact = msg.contact;
                String text = null;
                Attachment attachment = null;
                if (contact.id == owner.id) {
                    if (msg.text.contains("bot get")) {
                        text = parse(msg.text);
                        try {
                             attachment = new Attachment(MapSession, friendsMap, api , msg.text);
                        } catch (Exception e) {
                            logger.error("Ошибка получения документа");
                        }
                    } else if (msg.text.equals("help")) {
                        text = "Запрос состоит имеет ввид\n1)bot get: Иван Иванов\n2)bog get: всех\n3)bot get: Иванов Иван, Иванова Ивана 10/04/2015\n";
                    }
                    try {
                        Integer idMessage = msgApi.send(
                                contact.id,
                                null,
                                null,
                                null,
                                text,
                                null,
                                null,
                                null,
                                attachment.getAttachmentName(),
                                null,
                                null

                        );
                        logger.debug("Сообщение отправлено " + idMessage);

                        alreadySend = true;

                    } catch (IOException ex) {
                        logger.error("Ошибка отправки сообщения", ex);
                    } catch (Exception e) {
                        logger.debug("Ошибка attachment");
                    }
                }
            }

            break;

        case TIMEOUT: {
            putFriendsMap();

            for (Map.Entry<Long, Contact> current : friendsMap.entrySet()) {
                Long key = current.getValue().id;
              //  try {
                    if (!MapSession.get(key).isEmpty()) {
                        int index = MapSession.get(key).size() - 1;
                        Session lastSession = MapSession.get(key).get(index);
                        if ((lastSession.getEnd() == null) && current.getValue().online == 0) {
                            lastSession.setEnd(new Date());
                        } else if ((lastSession.getBegin() == null) && current.getValue().online == 1) {
                            Session session = new Session(new Date());
                            MapSession.get(key).add(session);
                        }
                    }
               // } catch (Exception e) {
                //    logger.error("Ошибка timeout");
                //}
            }
        }

        default:
         logger.debug("" + (event.object == null ? event.type : event.type + "|" + event.object));

        break;
    }


    }



    @Override
    public String description() {
        return  "Сервис статистики пользователей \"Вконтакте\"";
    }

    public  void firstPutOfFriends() {

            putFriendsMap();

            for (Map.Entry<Long, Contact> friend : friendsMap.entrySet()) {
                ArrayList<Session> arrayList = new ArrayList<>();
                if (friend.getValue().online == 1) {
                    Session session = new Session(new Date());
                    arrayList.add(session);
                    MapSession.put(friend.getValue().id, arrayList);
                    logger.debug(friend.getValue().lastName + " - онлайн");
                } else {
                    MapSession.put(friend.getValue().id, arrayList);
                    logger.debug(friend.getValue().lastName + " - оффлайн");
                }
            }

    }

    public void putFriendsMap() {
        try {
            friendsMap.clear();
            FriendsApi friendsApi = api.getFriends();
            Contact[] contacts = friendsApi.list(null, null, null, null, FRIENDS_FIELDS);
            for (Contact contact : contacts) {
                friendsMap.put(contact.id, contact);
            }
        } catch (Exception e) {
            logger.error("Ошибка получения списка друзей");
        }
    }

    private String parse(String msg) {
      try {
          String date = "";
          String text = "";
          try {
              Date date1 = new Date(msg.split(" ")[msg.split(" ").length - 1]);
              date = msg.split(" ")[msg.split(" ").length - 1];
          } catch (Exception e) {
              logger.error("даты нет");
          }

          if (msg.split(": ")[1].equals("всех")) {
              text = "Статисткика всех пользователй " + (date.isEmpty() ? "" : " за" + date);
          } else {
              String withoutDate = msg.substring(0, msg.lastIndexOf(" "));
              String[] arr = withoutDate.split(": ")[1].split(", ");
              String people = "";
              for (String humanName : arr) {
                  people += humanName + ", ";
                  text = "Статистика пользователей: " + people.substring(0, people.length() - 2) + (date.isEmpty() ? "" : " за " + date);
              }
          }
          return text;
      } catch (Exception e) {
          logger.error("Ошибка ввода");
      }
      return null;
    }



}

