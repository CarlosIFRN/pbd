CREATE DATABASE IF NOT EXISTS clinica;
USE clinica;

CREATE TABLE convenio (
    id_convenio INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    registro_ans CHAR(6) NOT NULL UNIQUE,
    ativo BOOLEAN DEFAULT TRUE
);

CREATE TABLE paciente (
    id_paciente INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cpf CHAR(11) NOT NULL UNIQUE, -- Otimizado para apenas números
    data_nascimento DATE NOT NULL,
    ddd CHAR(2) NOT NULL,
    numero VARCHAR(10) NOT NULL, -- Aumentado para prever máscaras com hífen
    id_convenio INT NULL, -- CORREÇÃO: Coluna criada (NULL para casos particulares)
    FOREIGN KEY (id_convenio) REFERENCES convenio(id_convenio)
);

CREATE TABLE especialidade (
    id_especialidade INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE medico (
    id_medico INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    crm VARCHAR(20) UNIQUE NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    id_especialidade INT NOT NULL,
    FOREIGN KEY (id_especialidade) REFERENCES especialidade(id_especialidade)
);

CREATE TABLE agenda (
    id_agenda INT AUTO_INCREMENT PRIMARY KEY,
    id_medico INT NOT NULL,
    situacao ENUM('Livre', 'Ocupado', 'Bloqueado') NOT NULL DEFAULT 'Livre',
    data_slot DATE NOT NULL,
    hora_slot TIME NOT NULL,
    UNIQUE (id_medico, data_slot, hora_slot), -- CORREÇÃO: UNIQUE por médico
    FOREIGN KEY (id_medico) REFERENCES medico(id_medico)
);

CREATE TABLE consulta (
    id_consulta INT AUTO_INCREMENT PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_agenda INT NOT NULL, -- Alterado para NOT NULL pois consulta precisa de horário
    status_consulta ENUM(
        'Agendada', 
        'Confirmada', 
        'Em Espera', 
        'Em Atendimento', 
        'Realizada', 
        'Cancelada', 
        'Faltou'
    ) DEFAULT 'Agendada',
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_agenda) REFERENCES agenda(id_agenda),
    UNIQUE (id_agenda) -- Garante que um slot de agenda só tenha uma consulta ativa
);

CREATE TABLE usuario (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE, -- Adicionado UNIQUE para evitar e-mails duplicados
    senha VARCHAR(255) NOT NULL,
    perfil ENUM('paciente', 'medico', 'administrador') NOT NULL DEFAULT 'administrador' -- Padronizado
);

CREATE TABLE prontuario (
    id_prontuario INT AUTO_INCREMENT PRIMARY KEY,
    id_consulta INT NOT NULL,
    descricao TEXT NOT NULL,
    prescricao TEXT NOT NULL,
    diagnostico TEXT NOT NULL,
    UNIQUE (id_consulta),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);