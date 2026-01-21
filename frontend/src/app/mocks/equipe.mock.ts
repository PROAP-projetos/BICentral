export type UserRole = 'viewer' | 'editor' | 'admin';

export interface TeamMember {
  id: number;
  name: string;
  email: string;
  role: UserRole;
}

export const MOCK_MEMBERS: TeamMember[] = [
  { id: 1, name: 'Ana Lima', email: 'ana.lima@bicentral.local', role: 'admin' },
  { id: 2, name: 'Bruno Costa', email: 'bruno.costa@bicentral.local', role: 'editor' },
  { id: 3, name: 'Carla Souza', email: 'carla.souza@bicentral.local', role: 'viewer' },
  { id: 4, name: 'Diego Rocha', email: 'diego.rocha@bicentral.local', role: 'editor' }
];
