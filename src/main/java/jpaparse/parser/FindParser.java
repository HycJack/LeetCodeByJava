package jpaparse.parser;

import jpaparse.KeyWordConstants;
import jpaparse.ParseException;
import jpaparse.Term;
import jpaparse.TermType;
import jpaparse.info.FindInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bruce.ge on 2016/12/4.
 */
public class FindParser {
    // there are the order

    // make sure orderby matched before by. finddistinct before find.
    private static String[] finds = {KeyWordConstants.FINDDISTINCT, KeyWordConstants.FIND};

    private static String[] linkOp = {KeyWordConstants.AND, KeyWordConstants.OR};

    private static String[] compareOp = {KeyWordConstants.BETWEEN, KeyWordConstants.GREATERTHAN, KeyWordConstants.LESSTHAN,
            KeyWordConstants.ISNOTNULL, KeyWordConstants.ISNULL, KeyWordConstants.NOTNULL, KeyWordConstants.NOTLIKE, KeyWordConstants.LIKE
            , KeyWordConstants.NOTIN, KeyWordConstants.NOT, KeyWordConstants.IN};

    private static String[] order = {KeyWordConstants.ASC, KeyWordConstants.DESC};


    public static String parse(String methodName, List<String> props, String tableName) {
        //first try to split them into terms.
        List<Term> terms = generateTerm(methodName, props);
        FindInfo info = buildFindInfo(terms);
        info.setTable(tableName);
        return buildQueryBy(info);
    }

    private static String buildQueryBy(FindInfo info) {
        StringBuilder queryBuilder = new StringBuilder();
        if (!info.getDistinct()) {
            queryBuilder.append("select" + info.getFetchPart());
        } else {
            queryBuilder.append("select dictinct(" + info.getFetchPart() + ")");
        }
        queryBuilder.append(info.getQueryPart());
        return queryBuilder.toString();
    }

    private static FindInfo buildFindInfo(List<Term> terms) {
        FindInfo info = new FindInfo();
        int state = 0;
        int s = 0;
        while (s < terms.size()) {
            Term cur = terms.get(s);
            switch (state) {
                case 0: {
                    if (cur.getTermType() == TermType.START_OP) {
                        if (cur.getValue().equals(KeyWordConstants.FINDDISTINCT)) {
                            info.setDistinct(true);
                            state = 2;
                            break;
                        } else {
                            state = 1;
                            break;
                        }
                    } else {
                        throw new ParseException("index is not find or insert or delete the value is:" + cur.getValue());
                    }
                }
                case 1: {
                    if (cur.getTermType() == TermType.ORDERBY) {
                        info.setQueryPart(info.getQueryPart() + " order by");
                        state = 3;
                        break;
                    } else if (cur.getTermType() == TermType.PROP) {
                        info.setFetchPart(info.getFetchPart() + " " + cur.getValue());
                        state = 6;
                        break;
                    } else {
                        throw new ParseException("the term after find is not legal, the term is " + cur.getValue());
                    }
                }
                case 2: {
                    if (cur.getTermType() == TermType.PROP) {
                        info.setFetchPart(cur.getValue());
                        state = 11;
                        break;
                    } else {
                        throw new ParseException("the term after findDistinct is not legal, the term is " + cur.getValue());
                    }
                }
                case 3: {
                    if (cur.getTermType() == TermType.PROP) {
                        info.setQueryPart(info.getQueryPart() + " " + cur.getValue());
                        state = 4;
                        break;
                    } else {
                        throw new ParseException("the term after order by is not legal, the term is " + cur.getValue());
                    }
                }
                case 4: {
                    if (cur.getTermType() == TermType.DESC) {
                        info.setQueryPart(info.getQueryPart() + " " + cur.getValue());
                        state = 5;
                        break;
                    } else {
                        //
                        throw new ParseException("the term after order by properter is not legal, the term is " + cur.getValue());
                    }
                }

                case 5: {
                    throw new ParseException("the term after desc asc etc is not legal, the term is " + cur.getValue());
                }

                case 6: {
                    if (cur.getTermType() == TermType.BY) {
                        info.setQueryPart(" where");
                        state = 8;
                        break;
                    } else if (cur.getTermType() == TermType.ORDERBY) {
                        info.setQueryPart(info.getQueryPart() + " order by");
                        state = 3;
                        break;
                    } else if (cur.getTermType() == TermType.LINK_OP) {
                        info.setFetchPart(info.getFetchPart() + " " + cur.getValue());
                        state = 7;
                        break;
                    } else {
                        throw new ParseException("the term shall be orderby or and/or, the term is " + cur.getValue());
                    }
                }

                case 7: {
                    if (cur.getTermType() == TermType.PROP) {
                        info.setFetchPart(info.getFetchPart() + " " + cur.getValue());
                        state = 6;
                        break;
                    } else {
                        throw new ParseException(" fetch part after and/or is not legal, the term is " + cur.getValue());
                    }
                }
                case 8: {
                    if (cur.getTermType() == TermType.PROP) {
//                        shall add with equalPart.
                        Integer paramCount = info.getParamCount();
                        String equalPart = " =" + "{" + paramCount + "}";
                        info.setParamCount(info.getParamCount() + 1);
                        info.setLastEqualLength(equalPart.length());
                        info.setLastQueryProp(cur.getValue());
                        info.setQueryPart(info.getQueryPart() + " " + cur.getValue() + equalPart);
                        state = 9;
                        break;
                    } else {
                        throw new ParseException(" the term after by is not legal, the term is " + cur.getValue());
                    }
                }

                case 9: {
                    if (cur.getTermType() == TermType.ORDERBY) {
                        info.setQueryPart(info.getQueryPart() + " order by");
                        state = 3;
                        break;
                    } else if (cur.getTermType() == TermType.COMPARE_OP) {
                        //first need to roll back the equal operator.
                        info.setParamCount(info.getParamCount() - 1);
                        info.setQueryPart(info.getQueryPart().substring(0, info.getQueryPart().length() - info.getLastEqualLength()));
                        handleWithCompare(info, cur);
                        state = 10;
                        break;
                    } else if (cur.getTermType() == TermType.LINK_OP) {
                        info.setQueryPart(info.getQueryPart() + " " + cur.getValue());
                        state = 8;
                        break;
                    } else {
                        throw new ParseException("query field after prop not legal, the term is:" + cur.getValue());
                    }
                }
                case 10: {
                    if (cur.getTermType() == TermType.ORDERBY) {
                        info.setQueryPart(info.getQueryPart() + " order by");
                        state = 3;
                        break;
                    } else if (cur.getTermType() == TermType.LINK_OP) {
                        info.setQueryPart(info.getQueryPart() + " " + cur.getValue());
                        state = 8;
                        break;
                    } else {
                        throw new ParseException("the term after compare is not legal, the term is:" + cur.getValue());
                    }
                }
                case 11: {
                    if (cur.getTermType() == TermType.ORDERBY) {
                        info.setQueryPart(info.getQueryPart() + " order by");
                        state = 3;
                        break;
                    } else if (cur.getTermType() == TermType.BY) {
                        info.setQueryPart(info.getQueryPart() + " where");
                        state = 8;
                        break;
                    } else {
                        throw new ParseException("the term after secect distinct() is not legal, the term is " + cur.getValue());
                    }
                }
            }
            s++;
        }
        if (state == 1 || state == 4 || state == 5 || state == 9 || state == 10 || state == 11 || state == 6) {
            return info;
        } else {
            throw new ParseException("the query not end legal, the fetch part is " + info.getFetchPart() + " the query part is " + info.getQueryPart());
        }
    }

    private static void handleWithCompare(FindInfo info, Term cur) {
        switch (cur.getValue()) {
            case KeyWordConstants.GREATERTHAN: {
                info.setQueryPart(info.getQueryPart() + " >{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.LESSTHAN: {
                info.setQueryPart(info.getQueryPart() + " <{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.BETWEEN: {
                info.setQueryPart(info.getQueryPart() + " >={" + info.getParamCount() + "} and " + info.getLastQueryProp() + " <={" + (info.getParamCount() + 1) + "}");
                info.setParamCount(info.getParamCount() + 2);
                break;
            }
            case KeyWordConstants.ISNOTNULL: {
                info.setQueryPart(info.getQueryPart() + " is not null");
                break;
            }
            case KeyWordConstants.ISNULL: {
                info.setQueryPart(info.getQueryPart() + " is null");
                info.setParamCount(info.getParamCount());
                break;
            }
            case KeyWordConstants.NOT: {
                info.setQueryPart(info.getQueryPart() + " !={" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.NOTIN: {
                info.setQueryPart(info.getQueryPart() + " not in{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.IN: {
                info.setQueryPart(info.getQueryPart() + " in{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.NOTLIKE: {
                info.setQueryPart(info.getQueryPart() + " not like{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }
            case KeyWordConstants.LIKE: {
                info.setQueryPart(info.getQueryPart() + " like{" + info.getParamCount() + "}");
                info.setParamCount(info.getParamCount() + 1);
                break;
            }

        }
    }


    /**
     * @param methodName
     * @return generate term to use with. there is time to check with them.
     * the compareOp shall after by keyword.  and desc after order by keyword.
     */
    private static List<Term> generateTerm(String methodName, List<String> props) {
        Map<Integer, Term> termMaps = new HashMap<Integer, Term>();
        List<Term> terms = new ArrayList<Term>();
        //try to match with things.
        boolean isBy = false;
        boolean isOrderBy = false;

        int used[] = new int[methodName.length()];
        //first parse with finds.
        for (String find : finds) {
            //find first to match with it.
            if (methodName.startsWith(find)) {
                for (int i = 0; i < find.length(); i++) {
                    used[i] = 1;
                }
                Term e = new Term(0, find.length(), TermType.START_OP, find);
                termMaps.put(0, e);
                break;
            }
        }
        //those that only exist one time.
        //first check with orderBy.
        // than check with props
        for (String prop : props) {
            prop = prop.toLowerCase();
            Pattern propPattern = Pattern.compile(prop);
            Matcher propMatcher = propPattern.matcher(methodName);
            while (propMatcher.find()) {
                int start = propMatcher.start();
                int end = propMatcher.end();
                if (used[start] != 1 && used[end - 1] != 1) {
                    //then add compare to term.
                    for (int i = start; i < end; i++) {
                        used[i] = 1;
                    }
                    Term e = new Term(start, end, TermType.PROP, prop);
                    termMaps.put(start, e);
                }
            }
        }
        int orderByStart = methodName.indexOf(KeyWordConstants.ORDERBY);
        if (orderByStart != -1) {
            isOrderBy = true;
            for (int i = orderByStart; i < orderByStart + KeyWordConstants.ORDERBY.length(); i++) {
                used[i] = 1;
            }
            Term e = new Term(orderByStart, orderByStart + KeyWordConstants.ORDERBY.length(), TermType.ORDERBY, KeyWordConstants.ORDERBY);
            termMaps.put(orderByStart, e);
        }

        //than check with by.  only find one time.
        Pattern by = Pattern.compile(KeyWordConstants.BY);
        Matcher matcher = by.matcher(methodName);
        while (matcher.find()) {
            int start = matcher.start();
            if (used[start] != 1) {
                int end = matcher.end();
                for (int i = start; i < end; i++) {
                    used[i] = 1;
                }
                isBy = true;
                Term e = new Term(start, end, TermType.BY, KeyWordConstants.BY);
                termMaps.put(start, e);
                break;
            }
        }

        //than find with and and or.
        for (String link : linkOp) {
            Pattern linkPattern = Pattern.compile(link);
            Matcher andMatcher = linkPattern.matcher(methodName);
            while (andMatcher.find()) {
                int start = andMatcher.start();
                if (used[start] != 1) {
                    int end = andMatcher.end();
                    for (int i = start; i < end; i++) {
                        used[i] = 1;
                    }
                    Term e = new Term(start, end, TermType.LINK_OP, link);
                    termMaps.put(start, e);
                }
            }
        }

        //if is by then shall check with comparator.
        if (isBy) {
            for (String compare : compareOp) {
                Pattern comparePattern = Pattern.compile(compare);
                Matcher compareMatcher = comparePattern.matcher(methodName);
                while (compareMatcher.find()) {
                    int start = compareMatcher.start();
                    int end = compareMatcher.end();
                    if (used[start] != 1 && used[end - 1] != 1) {
                        //then add compare to term.
                        for (int i = start; i < end; i++) {
                            used[i] = 1;
                        }
                        Term e = new Term(start, end, TermType.COMPARE_OP, compare);
                        termMaps.put(start, e);
                    }
                }
            }
        }
        //shall check the order time.
        if (isOrderBy) {
            for (String ordertype : order) {
                Pattern orderTypePattern = Pattern.compile(ordertype);
                Matcher orderTypeMatcher = orderTypePattern.matcher(methodName);
                while (orderTypeMatcher.find()) {
                    int start = orderTypeMatcher.start();
                    int end = orderTypeMatcher.end();
                    if (used[start] != 1 && used[end - 1] != 1) {
                        for (int i = start; i < end; i++) {
                            used[i] = 1;
                        }
                        Term term = new Term(start, end, TermType.DESC, ordertype);
                        termMaps.put(start, term);
                    }
                }
            }
        }

        int i = 0;
        String q = "";
        while (i < methodName.length()) {
            if (used[i] == 1) {
                if (q.length() > 0) {
                    throw new ParseException(" the property: '" + q + "' not in bean, the index range of wrong property is start with " + (i - 1 - q.length()) + " and end with " + (i - 1));
//                    terms.add(new Term(0, 0, TermType.PROP, q));
//                    q = "";
                }
                terms.add(termMaps.get(i));
                i = termMaps.get(i).getEnd();
            } else {
                q += methodName.charAt(i);
                i++;
            }
        }
        // than go to create the basic term. then add them to the queud.
        return terms;
    }


    public static void main(String[] args) {
        List<String> ll = new ArrayList<String>();
        ll.add("UserId");
        ll.add("password");
        ll.add("username");
        List<Term> terms = generateTerm("FINDDISTINCTUSERIDANDPASSWORDBYUSERNAMELIKEORDERBYUSERIDDESC".toLowerCase(), ll);
        System.out.println(parse("FINDUSERIDANDUSERNAMEBYUSERNAMELIKEANDUSERNAMEGREATERTHANANDPASSWORDBETWEENORDERBYUSERID".toLowerCase(), ll, "user"));

    }
}
