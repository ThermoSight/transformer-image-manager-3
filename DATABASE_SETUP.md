# Free PostgreSQL Database Setup with Supabase

## 🚀 Quick Setup (5 minutes)

### Step 1: Create Supabase Account

1. Go to [https://supabase.com](https://supabase.com)
2. Click **"Start your project"**
3. Sign up with GitHub (recommended) or email

### Step 2: Create Your Database

1. Click **"New Project"**
2. Choose your organization (or create one)
3. Fill in:
   - **Name**: `transformer-manager`
   - **Database Password**: Choose a strong password (save it!)
   - **Region**: Choose closest to your location
4. Click **"Create new project"**
5. ⏳ Wait 2-3 minutes for setup to complete

### Step 3: Get Connection Details

1. In your Supabase dashboard, go to **Settings** → **Database**
2. Scroll down to **Connection parameters**
3. Copy these values:
   - **Host**: `db.xxxxxxxxxxxxx.supabase.co`
   - **Database**: `postgres`
   - **User**: `postgres`
   - **Password**: (the one you set in step 2)
   - **Port**: `5432`

### Step 4: Update Application Properties

Open `transformer-manager-backkend/src/main/resources/application.properties` and replace:

```properties
# Replace YOUR_PROJECT_REF with your actual project reference
spring.datasource.url=jdbc:postgresql://db.YOUR_PROJECT_REF.supabase.co:5432/postgres

# Replace with your actual password
spring.datasource.password=YOUR_SUPABASE_PASSWORD
```

**Example:**

```properties
spring.datasource.url=jdbc:postgresql://db.abcdefghijklmnop.supabase.co:5432/postgres
spring.datasource.password=MyStrongPassword123!
```

### Step 5: Test Connection

1. Save the file
2. Run your Spring Boot application
3. ✅ It should connect successfully!

## 🆓 What You Get (Free Tier)

- **500 MB** database storage
- **2** concurrent connections
- **Unlimited** API requests
- **Built-in dashboard** for data management
- **Real-time subscriptions**
- **Row Level Security**

## 🔧 Alternative Options

### Option 2: Neon (Serverless PostgreSQL)

- Visit: [https://neon.tech](https://neon.tech)
- **Free tier**: 512MB, 1 database
- **Advantage**: Scales to zero when not used

### Option 3: Railway

- Visit: [https://railway.app](https://railway.app)
- **Free tier**: Good for development
- **Simple deployment**

## 🔍 Verification

After setup, your app should start without database errors. You can verify by:

1. Checking the Supabase dashboard for table creation
2. Accessing your app endpoints
3. No more "password authentication failed" errors

## 🆘 Troubleshooting

- **Connection timeout**: Check if your firewall blocks port 5432
- **Authentication failed**: Double-check password and project reference
- **SSL errors**: Supabase requires SSL, which is already configured

---

✅ **Ready to deploy!** Your database is now accessible from anywhere with internet connection.
