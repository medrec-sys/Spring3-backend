package fun.medrec.spring.interceptor;

public class UserContext {
    private static final ThreadLocal<Integer> id = new ThreadLocal<>();
    private static final ThreadLocal<String> account = new ThreadLocal<>();
    private static final ThreadLocal<String> username = new ThreadLocal<>();

    public static void set(Integer id, String account, String username) {
        UserContext.id.set(id);
        UserContext.account.set(account);
        UserContext.username.set(username);
    }

    public static void clear() {
        id.remove();
        account.remove();
        username.remove();
    }

    public static Integer getId() {
        return id.get();
    }

    public static String getAccount() {
        return account.get();
    }

    public static String getUsername() {
        return username.get();
    }
}
