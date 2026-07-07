import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { ITag } from 'app/shared/model/tag.model';
import { SubscriptionStatus } from 'app/shared/model/enumerations/subscription-status.model';
import { RecurrenceUnit } from 'app/shared/model/enumerations/recurrence-unit.model';

export interface IFinancialSubscription {
  id?: number;
  name?: string;
  description?: string | null;
  status?: keyof typeof SubscriptionStatus;
  expectedAmount?: number | null;
  amountTolerancePercentage?: number | null;
  currency?: string;
  recurrenceUnit?: keyof typeof RecurrenceUnit;
  intervalCount?: number;
  startDate?: dayjs.Dayjs;
  nextExpectedDate?: dayjs.Dayjs | null;
  endDate?: dayjs.Dayjs | null;
  automaticPayment?: boolean;
  notes?: string | null;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  account?: IFinancialAccount | null;
  category?: ICategory | null;
  tags?: ITag[] | null;
}

export const defaultValue: Readonly<IFinancialSubscription> = {
  automaticPayment: false,
};
