import { PrismaClient } from '@prisma/client';

// Single shared PrismaClient instance for the process. See prisma/schema.prisma for the schema
// and migrations (Prisma requires that directory to live at the project root, not under src/).
export const prisma = new PrismaClient();
