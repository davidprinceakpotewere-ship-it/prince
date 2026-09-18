# Odoo ERP Cloud Deployment & Infrastructure Migration 🚀

> A comprehensive technical breakdown of how I engineered, migrated, and hosted an enterprise-grade Odoo ERP deployment (focusing on HR & Payroll modules) on Microsoft Azure.

---

## 📋 Table of Contents
- [Executive Overview](#-executive-overview)
- [Cloud Infrastructure & Architecture](#-cloud-infrastructure--architecture)
- [Step-by-Step Deployment Guide](#-step-by-step-deployment-guide)
- [Engineering Obstacles & Troubleshooting Log](#-engineering-obstacles--troubleshooting-log)
- [Operational Verification & Hardening](#-operational-verification--hardening)

---

## 💡 Executive Overview
Initially, this Odoo ERP system (specializing in HR and Payroll management) operated within an isolated local workstation. To eliminate operational risks such as local hardware failure, lack of automated backups, and restricted remote access, I migrated the entire ERP system to **Microsoft Azure**. 

My goal was to build a production-ready, highly available, and secure cloud environment for remote administrative personnel while maintaining a strict operational budget ($15–$20 USD/month).

---

## 🛠 Cloud Infrastructure & Architecture

| Architecture Domain | Technical Specification | Engineering Rationale |
| :--- | :--- | :--- |
| **Cloud Provider** | Microsoft Azure Cloud Platform | Enterprise virtual network isolation & guaranteed 99.95% uptime SLA. |
| **Data Center Region** | South Africa (Johannesburg) | Minimal round-trip latency (RTT) for local network access. |
| **Operating System** | Ubuntu Linux Server (Headless CLI) | Eliminates desktop GUI memory bloat, dedicating resources to Odoo & PostgreSQL. |
| **Virtual Machine** | Burstable CPU Instance (Standard) | Handles heavy background payroll calculations without paying for idle capacity. |
| **Hardware Specs** | 4 vCPU Cores + 1 Dedicated GPU | Accelerates batch document generation, PDF rendering, and background tasks. |
| **Memory Setup** | 1 GB RAM + 2 GB Linux Swap File | Absorbs temporary memory spikes during large transfers on a 1 GB RAM instance. |
| **Database** | PostgreSQL | Strong ACID compliance, JSON data support, and native Odoo ORM integration. |
| **ERP Version** | Odoo Enterprise / Community v19 | Matches exact database schema structure and dependencies. |

---

## 🚀 Step-by-Step Deployment Guide

### Phase 1: Provisioning Microsoft Azure Infrastructure
1. Created an Azure Resource Group in the **South Africa (Johannesburg)** region.
2. Provisioned an Ubuntu Linux Virtual Machine with 4 vCPUs and headless CLI configuration.
3. Configured Network Security Groups (NSGs) to restrict unauthorized access while allowing secure inbound traffic.

### Phase 2: Operating System & Environment Setup
1. Connected via SSH and updated system packages (`sudo apt update && sudo apt upgrade -y`).
2. Configured a custom 2 GB Linux swap file (`/swapfile`) to prevent Out-Of-Memory (OOM) crashes on the 1 GB RAM instance.

### Phase 3: Installing Odoo v19 & PostgreSQL
1. Added official PostgreSQL repositories and installed PostgreSQL server.
2. Configured the Odoo system user (`odoo`), added official Odoo v19 repositories, and installed Odoo dependencies.
3. Configured `/etc/odoo/odoo.conf` with custom admin passwords, database filters, and worker thread limits.

### Phase 4: Database Migration & Schema Reconciliation
1. Extracted relational PostgreSQL database backups from the local environment (`.dump` / `.sql`).
2. Restored database schemas into the Azure PostgreSQL instance, ensuring alignment with Odoo v19 ORM architecture.

### Phase 5: Filestore Restoration
1. Transferred media archives (employee photos, PDF contracts, attachments) via SCP into `/var/lib/odoo/filestore/`.
2. Fixed directory ownership and file permissions using:
   ```bash
   sudo chown -R odoo:odoo /var/lib/odoo/
   ```

### Phase 6: Web Routing & Azure DNS Mapping
1. Assigned a custom DNS domain label to the Azure Public IP address via the Azure Portal.
2. Bound the Odoo web gateway to **TCP Port 8069**, enabling seamless browser access via a clean domain name.

---

## 🔍 Engineering Obstacles & Troubleshooting Log

| Technical Challenge | Root Cause Analysis | Engineering Remediation |
| :--- | :--- | :--- |
| **SSH Connection Timeout** | Azure NSG default rules blocked incoming ICMP ping traffic and SSH connections. | Created explicit Inbound Security Rules for TCP Port 22 and ICMP Echo. |
| **Headless OS CLI Learning Curve** | Ubuntu server runs without a GUI, requiring terminal-based management. | Mastered Linux CLI commands, `systemctl` service management, and text editing with `nano`/`vim`. |
| **Version Mismatch & HTTP 500** | Odoo 16 installation crashed when reading database tables created under Odoo 19 ORM. | Purged old installation, wiped database cluster, and performed clean Odoo v19 setup. |
| **Corrupted Filestore / Broken Media** | Incomplete archive decompression created nested file path errors for avatars/PDFs. | Re-uploaded raw `.zip` via SCP, performed clean extraction, and reset permissions with `chown`. |
| **Browser Sign-in Loops** | Client-side ad-blockers intercepted Odoo JSON-RPC calls as tracking scripts. | Instructed users to use Incognito Mode or whitelist the Azure domain in browser extensions. |
| **Memory Starvation (1GB RAM)** | Heavy PostgreSQL queries and Odoo workers triggered Linux OOM killer crashes. | Created a 2 GB Linux swap file, tuned PostgreSQL `shared_buffers`, and optimized workers. |

---

## 🔒 Operational Verification & System Hardening
- **Web Gateway Testing:** Verified HTTP/HTTPS connection cycles over TCP Port 8069 across Google Chrome, Microsoft Edge, and Mozilla Firefox.
- **Service Resilience:** Configured `systemd` to automatically restart Odoo and PostgreSQL services on server reboot.
- **Backup Automation:** Set up scheduled automated backups of PostgreSQL databases and filestore assets.

---
*Documented and deployed with precision.*


